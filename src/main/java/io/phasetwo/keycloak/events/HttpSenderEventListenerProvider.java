package io.phasetwo.keycloak.events;

import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_OK;

import com.github.xgp.util.BackOff;
import com.github.xgp.util.ExponentialBackOff;
import io.phasetwo.keycloak.events.format.DefaultPayloadFormatter;
import io.phasetwo.keycloak.events.format.FormattedPayload;
import io.phasetwo.keycloak.events.format.WebhookPayloadFormatter;
import io.phasetwo.keycloak.events.format.WebhookPayloadFormatters;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.broker.provider.util.LegacySimpleHttp;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;

@JBossLog
public class HttpSenderEventListenerProvider extends SenderEventListenerProvider {

  protected static final String TARGET_URI = "targetUri";
  protected static final String RETRY = "retry";
  protected static final String SHARED_SECRET = "sharedSecret";
  protected static final String HMAC_ALGORITHM = "hmacAlgorithm";
  protected static final String BACKOFF_INITIAL_INTERVAL = "backoffInitialInterval";
  protected static final String BACKOFF_MAX_ELAPSED_TIME = "backoffMaxElapsedTime";
  protected static final String BACKOFF_MAX_INTERVAL = "backoffMaxInterval";
  protected static final String BACKOFF_MULTIPLIER = "backoffMultiplier";
  protected static final String BACKOFF_RANDOMIZATION_FACTOR = "backoffRandomizationFactor";

  public HttpSenderEventListenerProvider(KeycloakSession session, ScheduledExecutorService exec) {
    super(session, exec);
  }

  @Override
  BackOff getBackOff() {
    boolean retry = getBooleanOr(config, RETRY, true);
    log.debugf("Retry is %b %s", retry, getOr(config, RETRY, "[empty]"));
    if (!retry) return BackOff.STOP_BACKOFF;
    else
      return new ExponentialBackOff.Builder()
          .setInitialIntervalMillis(getIntOr(config, BACKOFF_INITIAL_INTERVAL, 500))
          .setMaxElapsedTimeMillis(getIntOr(config, BACKOFF_MAX_ELAPSED_TIME, 900000))
          .setMaxIntervalMillis(getIntOr(config, BACKOFF_MAX_INTERVAL, 180000))
          .setMultiplier(getDoubleOr(config, BACKOFF_MULTIPLIER, 5))
          .setRandomizationFactor(getDoubleOr(config, BACKOFF_RANDOMIZATION_FACTOR, 0.5))
          .build();
  }

  String getTargetUri() {
    return config.get(TARGET_URI).toString();
  }

  Optional<String> getSharedSecret() {
    return Optional.ofNullable(config.get(SHARED_SECRET)).map(Object::toString);
  }

  Optional<String> getHmacAlgorithm() {
    return Optional.ofNullable(config.get(HMAC_ALGORITHM)).map(Object::toString);
  }

  /** ext-event-http: payload format id. Unset means the original event representation. */
  protected static final String FORMAT = "format";

  /** ext-event-http: formatter options, a JSON object of string values. */
  protected static final String FORMAT_CONFIG = "formatConfig";

  /** ext-event-http: static request headers, a JSON object of string values. */
  protected static final String HEADERS = "headers";

  Optional<String> getFormat() {
    return Optional.ofNullable(config == null ? null : config.get(FORMAT))
        .map(Object::toString)
        .filter(s -> !s.isBlank());
  }

  Map<String, String> getStringMap(String key) {
    Map<String, String> m = new LinkedHashMap<>();
    Object o = config == null ? null : config.get(key);
    if (o instanceof Map<?, ?> map) {
      map.forEach(
          (k, v) -> {
            if (k != null && v != null) m.put(k.toString(), v.toString());
          });
    }
    return m;
  }

  @Override
  public void onEvent(Event event) {
    if (getFormat().isEmpty()) {
      super.onEvent(event);
      return;
    }
    try {
      RealmModel realm = session.realms().getRealm(event.getRealmId());
      scheduleFormatted(new ExtendedAdminEvent(KeycloakModelUtils.generateId(), event, realm));
    } catch (Exception e) {
      log.warn("Error formatting event: " + event, e);
    }
  }

  @Override
  public void onEvent(AdminEvent event, boolean b) {
    if (getFormat().isEmpty()) {
      super.onEvent(event, b);
      return;
    }
    try {
      RealmModel realm = session.realms().getRealm(event.getRealmId());
      RealmModel authRealm =
          event.getAuthDetails() != null && event.getAuthDetails().getRealmId() != null
              ? session.realms().getRealm(event.getAuthDetails().getRealmId())
              : realm;
      scheduleFormatted(
          new ExtendedAdminEvent(KeycloakModelUtils.generateId(), event, realm, authRealm));
    } catch (Exception e) {
      log.warn("Error formatting admin event: " + event, e);
    }
  }

  private void scheduleFormatted(ExtendedAdminEvent event) throws IOException {
    String formatId = getFormat().get();
    WebhookPayloadFormatter formatter = WebhookPayloadFormatters.resolve(session, formatId);
    if (formatter == null) {
      log.warnf("Unknown payload format %s. Event %s not sent.", formatId, event.getId());
      return;
    }
    SenderTask task = new SenderTask(event, getBackOff());
    Map<String, String> formatConfig = getStringMap(FORMAT_CONFIG);
    task.setFormatter(formatter);
    task.setConfig(formatConfig);
    task.setPayload(formatter.format(List.of(event), formatConfig));
    schedule(task, 0l, TimeUnit.MILLISECONDS);
  }

  @Override
  void send(SenderTask task) throws SenderException, IOException {
    Optional<String> sharedSecret = getSharedSecret();
    Optional<String> algorithm = getHmacAlgorithm();
    Map<String, String> headers = getStringMap(HEADERS);
    send(
        task,
        getTargetUri(),
        request -> {
          headers.forEach(request::header);
          sharedSecret.ifPresent(
              secret ->
                  request.header(
                      "X-Keycloak-Signature",
                      hmacFor(payloadFor(task), secret, algorithm.orElse(HMAC_SHA256_ALGORITHM))));
        });
  }

  protected void send(
      SenderTask task, String targetUri, Optional<String> sharedSecret, Optional<String> algorithm)
      throws SenderException, IOException {
    send(
        task,
        targetUri,
        request ->
            sharedSecret.ifPresent(
                secret ->
                    request.header(
                        "X-Keycloak-Signature",
                        hmacFor(
                            payloadFor(task), secret, algorithm.orElse(HMAC_SHA256_ALGORITHM)))));
  }

  /**
   * The formatted payload for a task. Tasks scheduled without a formatter get the historical JSON
   * serialization of their event. Computed once and reused for every attempt.
   */
  protected FormattedPayload payloadFor(SenderTask task) {
    if (task.getPayload() == null) {
      try {
        task.setPayload(DefaultPayloadFormatter.formatObject(task.getEvent()));
      } catch (IOException e) {
        throw new UncheckedIOException("Unable to serialize webhook payload", e);
      }
    }
    return task.getPayload();
  }

  /**
   * Send the payload, applying an arbitrary authentication decorator (e.g. an HMAC signature header
   * or an {@code Authorization: Bearer} JWT) to the request before it is sent.
   */
  protected void send(SenderTask task, String targetUri, Consumer<LegacySimpleHttp> authDecorator)
      throws SenderException, IOException {
    task.incrementAndGetAttempt();
    log.debugf("attempting send to %s", targetUri);
    try (CloseableHttpClient http = HttpClients.createDefault()) {
      FormattedPayload payload = payloadFor(task);
      LegacySimpleHttp request =
          LegacySimpleHttp.doPost(targetUri, http).entity(new ByteArrayEntity(payload.body()));
      request.header(HttpHeaders.CONTENT_TYPE, payload.contentType());
      payload.headers().forEach(request::header);
      if (authDecorator != null) {
        authDecorator.accept(request);
      }
      LegacySimpleHttp.Response response = request.asResponse();
      int status = response.getStatus();
      log.debugf("sent to %s (%d)", targetUri, status);
      doAfterSend(task, status);
      if (status < HTTP_OK || status >= HTTP_MULT_CHOICE) { // any 2xx is acceptable
        log.warnf("Sending failure for %s (Server response:%d) - %s", targetUri, status, task);
        onFailureStatus(task, status);
        WebhookPayloadFormatter formatter = task.getFormatter();
        throw new SenderException(formatter == null || formatter.isRetryable(status));
      }
    } catch (SenderException se) {
      // rethrow existing SenderException
      throw se;
    } catch (Exception e) {
      log.warnf(e, "Sending exception to %s", targetUri);
      throw new SenderException(false, e);
    }
  }

  /** Hook for subclasses to react to a non-2xx response before the retry decision. */
  protected void onFailureStatus(SenderTask task, int httpStatus) {}

  protected final void doAfterSend(SenderTask task, int httpStatus) {
    try {
      afterSend(task, httpStatus);
    } catch (Exception e) {
      log.warn("Error afterSend", e);
    }
  }

  protected void afterSend(SenderTask task, int httpStatus) {}

  protected String hmacFor(FormattedPayload payload, String sharedSecret, String algorithm) {
    try {
      return calculateHmacSha(payload.body(), sharedSecret, algorithm);
    } catch (Exception e) {
      log.warn("Unable to sign data", e);
    }
    return "";
  }

  private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
  protected static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

  public static String calculateHmacSha(String data, String key, String algorithm)
      throws SignatureException {
    return calculateHmacSha(data.getBytes(StandardCharsets.UTF_8), key, algorithm);
  }

  public static String calculateHmacSha(byte[] data, String key, String algorithm)
      throws SignatureException {
    String result = null;
    try {
      SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), algorithm);
      Mac mac = Mac.getInstance(algorithm);
      mac.init(signingKey);
      byte[] digest = mac.doFinal(data);
      StringBuilder sb = new StringBuilder(digest.length * 2);
      String s;
      for (byte b : digest) {
        s = Integer.toHexString(0xFF & b);
        if (s.length() == 1) {
          sb.append('0');
        }
        sb.append(s);
      }
      result = sb.toString();
    } catch (Exception e) {
      throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
    }
    return result;
  }
}

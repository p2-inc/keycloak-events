package io.phasetwo.keycloak.events;

import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_OK;

import com.github.xgp.util.BackOff;
import com.github.xgp.util.ExponentialBackOff;
import io.phasetwo.keycloak.events.auth.HmacSigner;
import io.phasetwo.keycloak.events.format.DefaultPayloadFormatter;
import io.phasetwo.keycloak.events.format.FormattedPayload;
import io.phasetwo.keycloak.events.format.PayloadFormats;
import io.phasetwo.keycloak.events.format.WebhookPayloadFormatter;
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

  /** Serializes events for tasks scheduled without a formatter. */
  private final DefaultPayloadFormatter defaultFormatter = new DefaultPayloadFormatter();

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
      log.warnf(e, "Error formatting event %s", event.getId());
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
      log.warnf(e, "Error formatting admin event %s", event.getId());
    }
  }

  private void scheduleFormatted(ExtendedAdminEvent event) throws IOException {
    String formatId = getFormat().get();
    Optional<WebhookPayloadFormatter> resolved = new PayloadFormats(session).formatter(formatId);
    if (resolved.isEmpty()) {
      log.warnf("Unknown payload format %s. Event %s not sent.", formatId, event.getId());
      return;
    }
    WebhookPayloadFormatter formatter = resolved.get();
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
        task.setPayload(defaultFormatter.formatEvent(task.getEvent()));
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
      return new HmacSigner(algorithm, sharedSecret).sign(payload.body());
    } catch (Exception e) {
      log.warn("Unable to sign data", e);
    }
    return "";
  }

  private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
  protected static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

  /**
   * The hex HMAC of a string's UTF-8 bytes.
   *
   * @param data the data to sign
   * @param key the shared secret
   * @param algorithm a {@link javax.crypto.Mac} algorithm, e.g. {@code HmacSHA256}
   * @return the signature as lowercase hex
   * @throws SignatureException if signing fails
   * @deprecated use {@link HmacSigner}, which signs the exact payload bytes
   */
  @Deprecated
  public static String calculateHmacSha(String data, String key, String algorithm)
      throws SignatureException {
    return new HmacSigner(algorithm, key).sign(data.getBytes(StandardCharsets.UTF_8));
  }
}

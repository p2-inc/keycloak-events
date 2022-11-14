package io.phasetwo.keycloak.events;

import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_OK;

import com.github.xgp.util.BackOff;
import com.github.xgp.util.ExponentialBackOff;
import java.io.IOException;
import java.security.SignatureException;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.KeycloakSession;
import org.keycloak.util.JsonSerialization;

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
    if (getBooleanOr(config, RETRY, false)) return BackOff.STOP_BACKOFF;
    else
      return new ExponentialBackOff.Builder()
          .setInitialIntervalMillis(getIntOr(config, BACKOFF_INITIAL_INTERVAL, 500))
          .setMaxElapsedTimeMillis(getIntOr(config, BACKOFF_MAX_ELAPSED_TIME, 900000))
          .setMaxIntervalMillis(getIntOr(config, BACKOFF_MAX_INTERVAL, 60000))
          .setMultiplier(getDoubleOr(config, BACKOFF_MULTIPLIER, 1.5))
          .setRandomizationFactor(getDoubleOr(config, BACKOFF_RANDOMIZATION_FACTOR, 0.5))
          .build();
  }

  String getTargetUri() {
    return config.get(TARGET_URI).toString();
  }

  String getSharedSecret() {
    return config.get(SHARED_SECRET).toString();
  }

  Optional<String> getHmacAlgorithm() {
    Object a = config.get(HMAC_ALGORITHM);
    return a != null ? Optional.of(a.toString()) : Optional.empty();
  }

  @Override
  void send(SenderTask task) throws SenderException, IOException {
    send(task, getTargetUri(), getSharedSecret(), getHmacAlgorithm());
  }

  protected void send(
      SenderTask task, String targetUri, String sharedSecret, Optional<String> algorithm)
      throws SenderException, IOException {
    SimpleHttp request = SimpleHttp.doPost(targetUri, session).json(task.getEvent());
    if (sharedSecret != null) {
      request.header(
          "X-Keycloak-Signature",
          hmacFor(task.getEvent(), sharedSecret, algorithm.orElse(HMAC_SHA256_ALGORITHM)));
    }
    SimpleHttp.Response response = request.asResponse();
    int status = response.getStatus();
    log.debugf("sent to %s (%d)", targetUri, status);
    if (status < HTTP_OK || status >= HTTP_MULT_CHOICE) { // any 2xx is acceptable
      log.warnf("Sending failure (Server response:%d)", status);
      throw new SenderException(true);
    }
  }

  protected String hmacFor(Object o, String sharedSecret, String algorithm) {
    try {
      String data = JsonSerialization.writeValueAsString(o);
      return calculateHmacSha(data, sharedSecret, algorithm);
    } catch (Exception e) {
      log.warn("Unable to sign data", e);
    }
    return "";
  }

  private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
  private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

  public static String calculateHmacSha(String data, String key, String algorithm)
      throws SignatureException {
    String result = null;
    try {
      SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), algorithm);
      Mac mac = Mac.getInstance(algorithm);
      mac.init(signingKey);
      byte[] digest = mac.doFinal(data.getBytes());
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

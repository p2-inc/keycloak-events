package io.phasetwo.keycloak.events.format;

import io.phasetwo.keycloak.PublicAPI;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.keycloak.provider.Provider;

/**
 * Turns one or more webhook events into the exact bytes sent to a webhook receiver.
 *
 * <p>The formatted body is computed once per send and is used verbatim for the HTTP request body,
 * the {@code X-Keycloak-Signature} HMAC and the bearer JWT {@code request_body_sha256} claim, so
 * whatever a formatter returns is what the receiver can verify.
 *
 * <p>Implementations must be stateless and thread-safe: a formatter instance may be retained by a
 * send task and consulted (e.g. {@link #isRetryable(int)}) on a background thread after the session
 * that created it has closed.
 */
@PublicAPI(unstable = true)
public interface WebhookPayloadFormatter extends Provider {

  /**
   * Format the events into a request payload.
   *
   * @param events the events to send. Always contains at least one event and never more than
   *     {@link #maxBatchSize()}.
   * @param config the webhook's configuration (formatter options and authentication settings);
   *     never null
   * @return the payload to send
   */
  FormattedPayload format(List<ExtendedAdminEvent> events, Map<String, String> config)
      throws IOException;

  /** The maximum number of events this formatter can put in a single request. */
  default int maxBatchSize() {
    return 1;
  }

  /**
   * Whether a non-2xx HTTP response should be retried. The default retries every failure, which is
   * the historical webhook behavior.
   */
  default boolean isRetryable(int httpStatus) {
    return true;
  }

  @Override
  default void close() {}
}

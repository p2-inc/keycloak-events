package io.phasetwo.keycloak.events.format;

import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.keycloak.util.JsonSerialization;

/**
 * The historical keycloak-events payload: the event serialized as JSON, exactly as it was before
 * payload formats existed.
 */
public class DefaultPayloadFormatter implements WebhookPayloadFormatter {

  @Override
  public FormattedPayload format(List<ExtendedAdminEvent> events, Map<String, String> config)
      throws IOException {
    if (events.size() != 1) {
      throw new IllegalArgumentException("default format sends exactly one event per request");
    }
    return formatEvent(events.get(0));
  }

  /**
   * Serialize any event object the way webhooks always have. Used for ext-event-http, which sends
   * Keycloak's own event representations when no format is configured.
   *
   * @param event the event object
   * @return the JSON payload
   * @throws IOException if the event can't be serialized
   */
  public FormattedPayload formatEvent(Object event) throws IOException {
    return FormattedPayload.json(JsonSerialization.writeValueAsString(event));
  }
}

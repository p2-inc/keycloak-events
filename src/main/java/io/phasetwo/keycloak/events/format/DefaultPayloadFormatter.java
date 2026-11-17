package io.phasetwo.keycloak.events.format;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.keycloak.models.KeycloakSession;
import org.keycloak.util.JsonSerialization;

/**
 * The historical keycloak-events payload: the event serialized as JSON, exactly as it was before
 * payload formats existed.
 */
public class DefaultPayloadFormatter implements WebhookPayloadFormatter {

  public static final String ID = "default";

  static final DefaultPayloadFormatter INSTANCE = new DefaultPayloadFormatter();

  @Override
  public FormattedPayload format(List<ExtendedAdminEvent> events, Map<String, String> config)
      throws IOException {
    if (events.size() != 1) {
      throw new IllegalArgumentException("default format sends exactly one event per request");
    }
    return formatObject(events.get(0));
  }

  /** Serialize any event object the way webhooks always have. */
  public static FormattedPayload formatObject(Object event) throws IOException {
    return FormattedPayload.json(JsonSerialization.writeValueAsString(event));
  }

  @AutoService(WebhookPayloadFormatterFactory.class)
  public static class Factory implements WebhookPayloadFormatterFactory {
    @Override
    public WebhookPayloadFormatter create(KeycloakSession session) {
      return INSTANCE;
    }

    @Override
    public String getId() {
      return ID;
    }

    @Override
    public String getHelpText() {
      return "The keycloak-events JSON payload (default).";
    }
  }
}

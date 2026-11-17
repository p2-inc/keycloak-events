package io.phasetwo.keycloak.events.format;

import com.google.auto.service.AutoService;
import org.keycloak.models.KeycloakSession;

/** Registers {@link GoogleSecOpsPayloadFormatter} as the {@code google-secops} payload format. */
@AutoService(WebhookPayloadFormatterFactory.class)
public class GoogleSecOpsPayloadFormatterFactory implements WebhookPayloadFormatterFactory {

  public static final String PROVIDER_ID = "google-secops";

  // stateless and thread-safe, so one instance serves every session
  private final GoogleSecOpsPayloadFormatter formatter = new GoogleSecOpsPayloadFormatter();

  @Override
  public WebhookPayloadFormatter create(KeycloakSession session) {
    return formatter;
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getHelpText() {
    return "Google SecOps webhook feed with log type KEYCLOAK (POST to the feed's"
        + " ...:importPushLogs endpoint). Use authType 'headers' with config"
        + " header.x-goog-chronicle-auth (API key) and header.x-chronicle-auth (feed secret).";
  }
}

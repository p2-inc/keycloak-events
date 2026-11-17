package io.phasetwo.keycloak.events.format;

import com.google.auto.service.AutoService;
import java.util.List;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

/** Registers {@link DatadogPayloadFormatter} as the {@code datadog} payload format. */
@AutoService(WebhookPayloadFormatterFactory.class)
public class DatadogPayloadFormatterFactory implements WebhookPayloadFormatterFactory {

  public static final String PROVIDER_ID = "datadog";

  // stateless and thread-safe, so one instance serves every session
  private final DatadogPayloadFormatter formatter = new DatadogPayloadFormatter();

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
    return "Datadog Logs intake (POST https://http-intake.logs.<site>/api/v2/logs). Use authType"
        + " 'headers' with config header.DD-API-KEY. Entries use ddsource=keycloak and"
        + " Keycloak's native event log line, so Datadog's Keycloak pipeline and Cloud SIEM"
        + " content pack apply.";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return ProviderConfigurationBuilder.create()
        .property()
        .name(DatadogPayloadFormatter.CONFIG_SERVICE)
        .label("Service")
        .helpText("The Datadog service attribute.")
        .type(ProviderConfigProperty.STRING_TYPE)
        .defaultValue(DatadogPayloadFormatter.DEFAULT_SERVICE)
        .add()
        .property()
        .name(DatadogPayloadFormatter.CONFIG_SOURCE)
        .label("Source")
        .helpText("The ddsource attribute. Keep 'keycloak' to use Datadog's Keycloak integration.")
        .type(ProviderConfigProperty.STRING_TYPE)
        .defaultValue(DatadogPayloadFormatter.DEFAULT_SOURCE)
        .add()
        .property()
        .name(DatadogPayloadFormatter.CONFIG_HOSTNAME)
        .label("Hostname")
        .helpText("The hostname attribute. Defaults to this Keycloak node's hostname.")
        .type(ProviderConfigProperty.STRING_TYPE)
        .add()
        .property()
        .name(DatadogPayloadFormatter.CONFIG_TAGS)
        .label("Tags")
        .helpText("Comma separated ddtags, e.g. env:prod,team:iam.")
        .type(ProviderConfigProperty.STRING_TYPE)
        .add()
        .build();
  }
}

package io.phasetwo.keycloak.events.format;

import com.google.auto.service.AutoService;
import org.keycloak.models.KeycloakSession;

/** Registers {@link AzureSentinelPayloadFormatter} as the {@code azure-sentinel} payload format. */
@AutoService(WebhookPayloadFormatterFactory.class)
public class AzureSentinelPayloadFormatterFactory implements WebhookPayloadFormatterFactory {

  public static final String PROVIDER_ID = "azure-sentinel";

  // stateless and thread-safe, so one instance serves every session
  private final AzureSentinelPayloadFormatter formatter = new AzureSentinelPayloadFormatter();

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
    return "Microsoft Sentinel via the Azure Monitor Logs Ingestion API (POST"
        + " {endpoint}/dataCollectionRules/{dcrImmutableId}/streams/{stream}?api-version=2023-01-01)."
        + " Use authType 'oauth2-client-credentials' with oauth2.tokenUrl"
        + " https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token, oauth2.clientId,"
        + " oauth2.clientSecret and oauth2.scope https://monitor.azure.com//.default. Stream"
        + " columns: "
        + formatter.streamColumnsJson();
  }
}

package io.phasetwo.keycloak.events.format;

import com.google.auto.service.AutoService;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/**
 * SPI for webhook payload formats. The server-wide default format is chosen with {@code
 * --spi-webhook-payload-formatter--provider=<id>}; individual webhooks can override it with their
 * {@code payloadFormat}.
 */
@AutoService(Spi.class)
public class WebhookPayloadFormatterSpi implements Spi {

  public static final String NAME = "webhook-payload-formatter";

  @Override
  public boolean isInternal() {
    return false;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Class<? extends Provider> getProviderClass() {
    return WebhookPayloadFormatter.class;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Class<? extends ProviderFactory> getProviderFactoryClass() {
    return WebhookPayloadFormatterFactory.class;
  }
}

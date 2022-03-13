package io.phasetwo.keycloak.resources;

import io.phasetwo.keycloak.model.WebhookProvider;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resource.RealmResourceProvider;

public class WebhooksResourceProvider implements RealmResourceProvider {

  private final KeycloakSession session;

  public WebhooksResourceProvider(KeycloakSession session) {
    this.session = session;
  }

  @Override
  public void close() {}

  @Override
  public Object getResource() {
    RealmModel realm = session.getContext().getRealm();
    WebhooksResource webhooks =
        new WebhooksResource(realm, session.getProvider(WebhookProvider.class));
    ResteasyProviderFactory.getInstance().injectProperties(webhooks);
    webhooks.setup();
    return webhooks;
  }
}

package io.phasetwo.keycloak.resources;

import io.phasetwo.keycloak.model.WebhookProvider;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public class WebhooksResourceProvider extends BaseRealmResourceProvider {

  public WebhooksResourceProvider(KeycloakSession session) {
    super(session);
  }

  @Override
  public Object getRealmResource() {
    RealmModel realm = session.getContext().getRealm();
    WebhooksResource webhooks =
        new WebhooksResource(realm, session.getProvider(WebhookProvider.class));
    ResteasyProviderFactory.getInstance().injectProperties(webhooks);
    webhooks.setup();
    return webhooks;
  }
}

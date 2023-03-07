package io.phasetwo.keycloak.resources;

import org.keycloak.models.KeycloakSession;

public class WebhooksResourceProvider extends BaseRealmResourceProvider {

  public WebhooksResourceProvider(KeycloakSession session) {
    super(session);
  }

  @Override
  public Object getRealmResource() {
    WebhooksResource webhooks = new WebhooksResource(session);
    webhooks.setup();
    return webhooks;
  }
}

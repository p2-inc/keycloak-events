package io.phasetwo.keycloak.resources;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resource.RealmResourceProvider;

public class RealmAttributesResourceProvider implements RealmResourceProvider {

  private final KeycloakSession session;

  public RealmAttributesResourceProvider(KeycloakSession session) {
    this.session = session;
  }

  @Override
  public void close() {}

  @Override
  public Object getResource() {
    RealmModel realm = session.getContext().getRealm();
    RealmAttributesResource realmAttributes = new RealmAttributesResource(realm);
    ResteasyProviderFactory.getInstance().injectProperties(realmAttributes);
    realmAttributes.setup();
    return realmAttributes;
  }
}

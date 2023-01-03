package io.phasetwo.keycloak.resources;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public class RealmAttributesResourceProvider extends BaseRealmResourceProvider {

  public RealmAttributesResourceProvider(KeycloakSession session) {
    super(session);
  }

  @Override
  public Object getRealmResource() {
    RealmModel realm = session.getContext().getRealm();
    RealmAttributesResource realmAttributes = new RealmAttributesResource(realm);
    ResteasyProviderFactory.getInstance().injectProperties(realmAttributes);
    realmAttributes.setup();
    return realmAttributes;
  }
}

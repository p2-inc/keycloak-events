package io.phasetwo.keycloak.resources;

import org.keycloak.models.KeycloakSession;

public class RealmAttributesResourceProvider extends BaseRealmResourceProvider {

  public RealmAttributesResourceProvider(KeycloakSession session) {
    super(session);
  }

  @Override
  public Object getRealmResource() {
    RealmAttributesResource realmAttributes = new RealmAttributesResource(session);
    realmAttributes.setup();
    return realmAttributes;
  }
}

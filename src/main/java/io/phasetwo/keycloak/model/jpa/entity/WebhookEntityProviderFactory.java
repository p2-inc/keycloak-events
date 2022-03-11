package io.phasetwo.keycloak.model.jpa.entity;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.model.WebhookProvider;
import org.keycloak.Config.Scope;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

/** */
@AutoService(JpaEntityProviderFactory.class)
public class WebhookEntityProviderFactory implements JpaEntityProviderFactory {

  protected static final String ID = "webhook-entity-provider";

  @Override
  public JpaEntityProvider create(KeycloakSession session) {
    return new WebhookEntityProvider();
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public void init(Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    factory.register(
        (event) -> {
          if (event instanceof RealmModel.RealmRemovedEvent)
            realmRemoved((RealmModel.RealmRemovedEvent) event);
        });
  }

  @Override
  public void close() {}

  private void realmRemoved(RealmModel.RealmRemovedEvent event) {
    event.getKeycloakSession().getProvider(WebhookProvider.class).removeWebhooks(event.getRealm());
  }
}

package io.phasetwo.keycloak.model.jpa;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.model.WebhookProviderFactory;
import javax.persistence.EntityManager;
import org.keycloak.Config.Scope;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

@AutoService(WebhookProviderFactory.class)
public class JpaWebhookProviderFactory implements WebhookProviderFactory {

  public static final String PROVIDER_ID = "jpa-webhook";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public WebhookProvider create(KeycloakSession session) {
    EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
    return new JpaWebhookProvider(session, em);
  }

  @Override
  public void init(Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}

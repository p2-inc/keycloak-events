package io.phasetwo.keycloak.model.jpa.entity;

import java.util.Arrays;
import java.util.List;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;

public class WebhookEntityProvider implements JpaEntityProvider {

  private static Class<?>[] entities = {WebhookEntity.class};

  @Override
  public List<Class<?>> getEntities() {
    return Arrays.<Class<?>>asList(entities);
  }

  @Override
  public String getChangelogLocation() {
    return "META-INF/jpa-changelog-events-main.xml";
  }

  @Override
  public void close() {}

  @Override
  public String getFactoryId() {
    return WebhookEntityProviderFactory.ID;
  }
}

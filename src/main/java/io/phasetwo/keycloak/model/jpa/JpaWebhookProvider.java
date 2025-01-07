package io.phasetwo.keycloak.model.jpa;

import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.model.jpa.entity.WebhookEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.stream.Stream;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

public class JpaWebhookProvider implements WebhookProvider {

  protected final KeycloakSession session;
  protected final EntityManager em;

  public JpaWebhookProvider(KeycloakSession session, EntityManager em) {
    this.session = session;
    this.em = em;
  }

  @Override
  public WebhookModel createWebhook(RealmModel realm, String url, UserModel createdBy) {
    WebhookEntity e = new WebhookEntity();
    e.setId(KeycloakModelUtils.generateId());
    e.setRealmId(realm.getId());
    e.setUrl(url);
    e.setCreatedBy(createdBy.getId());
    em.persist(e);
    em.flush();
    WebhookModel webhook = new WebhookAdapter(session, realm, em, e);
    return webhook;
  }

  @Override
  public WebhookModel getWebhookById(RealmModel realm, String id) {
    WebhookEntity webhook = em.find(WebhookEntity.class, id);
    if (webhook != null && webhook.getRealmId().equals(realm.getId())) {
      return new WebhookAdapter(session, realm, em, webhook);
    } else {
      return null;
    }
  }

  @Override
  public Stream<WebhookModel> getWebhooksStream(
      RealmModel realm, Integer firstResult, Integer maxResults) {
    TypedQuery<WebhookEntity> query =
        em.createNamedQuery("getWebhooksByRealmId", WebhookEntity.class);
    query.setParameter("realmId", realm.getId());
    if (firstResult != null) query.setFirstResult(firstResult);
    if (maxResults != null) query.setMaxResults(maxResults);
    return query.getResultStream().map(e -> new WebhookAdapter(session, realm, em, e));
  }

  @Override
  public Long getWebhooksCount(RealmModel realm) {
    TypedQuery<Long> query = em.createNamedQuery("countWebhooksByRealmId", Long.class);
    query.setParameter("realmId", realm.getId());
    return query.getSingleResult();
  }

  @Override
  public boolean removeWebhook(RealmModel realm, String id) {
    WebhookEntity e = em.find(WebhookEntity.class, id);
    em.remove(e);
    em.flush();
    return true;
  }

  @Override
  public void removeWebhooks(RealmModel realm) {
    Query query = em.createNamedQuery("removeAllWebhooks");
    query.setParameter("realmId", realm.getId());
    query.executeUpdate();
  }

  @Override
  public void close() {}
}

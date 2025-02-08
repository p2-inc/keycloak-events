package io.phasetwo.keycloak.model.jpa;

import io.phasetwo.keycloak.model.KeycloakEventType;
import io.phasetwo.keycloak.model.WebhookEventModel;
import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.model.WebhookSendModel;
import io.phasetwo.keycloak.model.jpa.entity.WebhookEntity;
import io.phasetwo.keycloak.model.jpa.entity.WebhookEventEntity;
import io.phasetwo.keycloak.model.jpa.entity.WebhookSendEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.io.IOException;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.util.JsonSerialization;

@JBossLog
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
  public WebhookSendModel getSendById(RealmModel realm, String id) {
    WebhookSendEntity send = em.find(WebhookSendEntity.class, id);
    if (send != null && send.getWebhook().getRealmId().equals(realm.getId())) {
      return new WebhookSendAdapter(session, realm, em, send);
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
  public WebhookEventModel storeEvent(
      RealmModel realm, KeycloakEventType type, String id, Object eventObject) {
    WebhookEventEntity e = new WebhookEventEntity();
    e.setId(KeycloakModelUtils.generateId());
    e.setRealmId(realm.getId());
    e.setEventType(type);
    if (type == KeycloakEventType.USER) e.setEventId(id);
    if (type == KeycloakEventType.ADMIN) e.setAdminEventId(id);
    try {
      e.setEventObject(JsonSerialization.writeValueAsString(eventObject));
    } catch (IOException ioe) {
      log.warn("Error serializing event object", ioe);
    }
    em.persist(e);
    em.flush();
    WebhookEventModel event = new WebhookEventAdapter(session, realm, em, e);
    return event;
  }

  @Override
  public WebhookEventModel getEvent(RealmModel realm, KeycloakEventType type, String id) {
    String named =
        (type == KeycloakEventType.USER)
            ? "getWebhookEventByEventId"
            : "getWebhookEventByAdminEventId";
    TypedQuery<WebhookEventEntity> query = em.createNamedQuery(named, WebhookEventEntity.class);
    query.setParameter("realmId", realm.getId());
    query.setParameter("id", id);
    try {
      WebhookEventEntity event = query.getSingleResult();
      if (event != null) {
        return new WebhookEventAdapter(session, realm, em, event);
      }
    } catch (NoResultException nre) {
      log.tracef("no result for event %s %s %s", realm.getId(), type, id);
    } catch (Exception ignore) {
      log.warnf(ignore, "error fetching event %s %s %s", realm.getId(), type, id);
    }
    return null;
  }

  @Override
  public Stream<WebhookSendModel> getSends(
      RealmModel realm, WebhookModel webhook, Integer firstResult, Integer maxResults) {
    TypedQuery<WebhookSendEntity> query =
        em.createNamedQuery("getWebhookSendsByWebhook", WebhookSendEntity.class);
    query.setParameter("webhook", ((WebhookAdapter) webhook).getEntity());
    if (firstResult != null) query.setFirstResult(firstResult);
    if (maxResults != null) query.setMaxResults(maxResults);
    return query.getResultStream().map(e -> new WebhookSendAdapter(session, realm, em, e));
  }

  @Override
  public WebhookSendModel storeSend(
      WebhookModel webhook, WebhookEventModel event, String id, String type) {
    WebhookSendEntity e = em.find(WebhookSendEntity.class, id);
    if (e == null) {
      e = new WebhookSendEntity();
      e.setId(id);
    }
    e.setEventType(type);
    e.setWebhook(((WebhookAdapter) webhook).getEntity());
    e.setEvent(((WebhookEventAdapter) event).getEntity());
    em.persist(e);
    em.flush();
    WebhookSendModel send = new WebhookSendAdapter(session, event.getRealm(), em, e);
    return send;
  }

  @Override
  public Stream<WebhookSendModel> getSends(RealmModel realm, WebhookEventModel event) {
    TypedQuery<WebhookSendEntity> query =
        em.createNamedQuery("getWebhookSendsByEvent", WebhookSendEntity.class);
    query.setParameter("event", event);
    return query.getResultStream().map(e -> new WebhookSendAdapter(session, realm, em, e));
  }

  @Override
  public void close() {}
}

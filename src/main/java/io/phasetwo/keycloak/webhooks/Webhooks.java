package io.phasetwo.keycloak.webhooks;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Joiner;
import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

/** Common utilities for webhooks */
public final class Webhooks {

  public static final String WEBHOOKS_ADMIN_UI_PROVIDER_ID = "Webhooks";

  /**
   * Create a ComponentModel, given an existing, persisted WebhookModel. Note this doesn't persist
   * the ComponentModel, nor does it update the WebhookModel componentId.
   */
  public static ComponentModel createComponentForWebhook(
      KeycloakSession session, RealmModel r, WebhookModel w) {
    ComponentModel c = new ComponentModel();
    c.setId(KeycloakModelUtils.generateId());
    c.setParentId(r.getId());
    c.setProviderId(WEBHOOKS_ADMIN_UI_PROVIDER_ID);
    c.setProviderType("org.keycloak.services.ui.extend.UiPageProvider");
    // config
    mergeComponent(c, w);
    return c;
  }

  /** Put the ComponentModel values INTO the WebhookModel */
  public static void mergeWebhook(WebhookModel w, ComponentModel c) {
    w.setUrl(c.get("url"));
    w.setEnabled(c.get("enabled", true));
    List<String> evts = c.getConfig().get("eventTypes");
    if (evts != null && evts.size() > 0) {
      w.removeEventTypes();
      evts.forEach(t -> w.addEventType(t));
    }
    String secret = c.get("secret");
    if (!isNullOrEmpty(secret)) {
      w.setSecret(secret);
    }
    String algorithm = c.get("algorithm");
    if (!isNullOrEmpty(algorithm)) {
      w.setAlgorithm(algorithm);
    } else {
      w.setAlgorithm("HmacSHA256");
    }
    w.setComponentId(c.getId());
  }

  /**
   * Put the WebhookModel values INTO the ComponentModel. Assumes the ComponentModel ID is already
   * set.
   */
  public static void mergeComponent(ComponentModel c, WebhookModel w) {
    c.put("url", w.getUrl());
    c.put("enabled", w.isEnabled());
    c.put("secret", w.getSecret());
    c.put("algorithm", w.getAlgorithm());
    // - eventTypes
    MultivaluedHashMap<String, String> config = c.getConfig();
    if (config == null) config = new MultivaluedHashMap<String, String>();
    final List<String> evts = new ArrayList<String>();
    if (w.getEventTypes() != null && w.getEventTypes().size() > 0) {
      w.getEventTypes().forEach(t -> evts.add(t));
    }
    config.put("eventTypes", evts);
    c.setConfig(config);
  }

  /** Put the WebhookRepresentation INTO the WebhookModel */
  public static void mergeWebhook(WebhookRepresentation rep, WebhookModel w) {
    w.setUrl(rep.getUrl());
    w.setEnabled(rep.isEnabled());
    if (rep.getEventTypes() != null) {
      w.removeEventTypes();
      rep.getEventTypes().forEach(t -> w.addEventType(t));
    }
    if (rep.getSecret() != null && !"".equals(rep.getSecret())) {
      w.setSecret(rep.getSecret());
    }
    if (rep.getAlgorithm() != null && !"".equals(rep.getAlgorithm())) {
      w.setAlgorithm(rep.getAlgorithm());
    } else {
      w.setAlgorithm("HmacSHA256");
    }
  }

  /** Convert a WebhookModel to a WebhookRepresentation */
  public static WebhookRepresentation toRepresentation(WebhookModel w) {
    WebhookRepresentation webhook = new WebhookRepresentation();
    webhook.setId(w.getId());
    webhook.setEnabled(w.isEnabled());
    webhook.setUrl(w.getUrl());
    UserModel u = w.getCreatedBy();
    if (u != null) {
      webhook.setCreatedBy(u.getId());
    }
    webhook.setCreatedAt(w.getCreatedAt());
    webhook.setRealm(w.getRealm().getName());
    webhook.setEventTypes(w.getEventTypes());
    // no secret
    return webhook;
  }

  public static String componentModelToString(ComponentModel c) {
    StringBuilder o = new StringBuilder();
    o.append("id=").append(c.getId()).append(", ");
    o.append("name=").append(c.getName()).append(", ");
    o.append("parentId=").append(c.getParentId()).append(", ");
    o.append("providerId=").append(c.getProviderId()).append(", ");
    o.append("providerType=").append(c.getProviderType()).append(", ");
    o.append("subType=").append(c.getSubType()).append(", ");
    //    o.append("hasNote=").append(""+c.hasNote()).append(", ");
    o.append("config=").append(mapToString(c.getConfig()));
    return o.toString();
  }

  public static String mapToString(Map<String, List<String>> config) {
    if (config == null) return "[]";
    else return "[" + Joiner.on(",").withKeyValueSeparator("=").join(config) + "]";
  }
}

package io.phasetwo.keycloak.ui;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.auto.service.AutoService;
import com.google.common.base.Joiner;
import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.services.ui.extend.UiPageProvider;
import org.keycloak.services.ui.extend.UiPageProviderFactory;

@JBossLog
@AutoService(UiPageProviderFactory.class)
public class WebhookAdminUiPage implements UiPageProvider, UiPageProviderFactory<ComponentModel> {

  // in postInit do a migration of existing webhooks
  // do we need to keep track of the created component id to link the two?
  // the crud componentModel methods are on the RealmModel
  // we'll need to update the ComponentModel from the webhook resource methods

  static String mapToString(Map<String, List<String>> config) {
    if (config == null) return "[]";
    else return "[" + Joiner.on(",").withKeyValueSeparator("=").join(config) + "]";
  }

  static String componentModelToString(ComponentModel c) {
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

  @Override
  public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
    // Called after a component is created
    log.debugf("[realmId=%s] onCreate: %s", realm.getId(), componentModelToString(model));

    WebhookProvider webhooks = session.getProvider(WebhookProvider.class);
    WebhookModel w = webhooks.createWebhook(realm, model.get("url"));
    mergeWebhook(w, model);
  }

  // put the ComponentModel values INTO the WebhookModel
  static void mergeWebhook(WebhookModel w, ComponentModel c) {
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

  // put the WebhookModel values INTO the ComponentModel
  static void mergeComponent(ComponentModel c, WebhookModel w) {}

  @Override
  public void onUpdate(
      KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
    // Called after the component is updated.
    log.debugf(
        "[realmId=%s] onUpdate: %s => %s",
        realm.getId(), componentModelToString(oldModel), componentModelToString(newModel));

    WebhookProvider webhooks = session.getProvider(WebhookProvider.class);
    WebhookModel w = webhooks.getWebhookByComponentId(realm, oldModel.getId());
    mergeWebhook(w, newModel);
  }

  @Override
  public void preRemove(KeycloakSession session, RealmModel realm, ComponentModel model) {
    // Called before the component is removed.
    log.debugf("[realmId=%s] preRemove: %s", realm.getId(), componentModelToString(model));

    WebhookProvider webhooks = session.getProvider(WebhookProvider.class);
    try {
      WebhookModel w = webhooks.getWebhookByComponentId(realm, model.getId());
      webhooks.removeWebhook(realm, w.getId());
    } catch (Exception e) {
      log.warnf("Error deleting webhook: %s", e.getMessage());
    }
  }

  @Override
  public void validateConfiguration(
      KeycloakSession session, RealmModel realm, ComponentModel model) {
    // Called before a component is created or updated.
    log.debugf(
        "[realmId=%s] validateConfiguration: %s", realm.getId(), componentModelToString(model));
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    KeycloakModelUtils.runJobInTransaction(
        factory,
        session -> {
          session
              .realms()
              .getRealmsStream()
              .forEach(
                  realm -> {
                    WebhookProvider webhooks = session.getProvider(WebhookProvider.class);
                    webhooks
                        .getWebhooksStream(realm)
                        .filter(w -> isNullOrEmpty(w.getComponentId()))
                        .forEach(
                            w -> {
                              createComponentForWebhook(session, realm, w);
                            });
                  });
        });
  }

  void createComponentForWebhook(KeycloakSession session, RealmModel r, WebhookModel w) {
    ComponentModel c = new ComponentModel();
    c.setId(KeycloakModelUtils.generateId());
    c.setParentId(r.getId());
    c.setProviderId(getId());
    c.setProviderType("org.keycloak.services.ui.extend.UiPageProvider");
    // config
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

  @Override
  public void close() {}

  @Override
  public String getId() {
    return "Webhooks";
  }

  @Override
  public String getHelpText() {
    return "Create and manage webhooks for events";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return ProviderConfigurationBuilder.create()
        .property()
        .name("enabled")
        .label("Enabled")
        .required(true)
        .defaultValue(true)
        .helpText("Is this webhook enabled")
        .type(ProviderConfigProperty.BOOLEAN_TYPE)
        .add()
        .property()
        .name("url")
        .label("URL")
        .required(true)
        .helpText("Webhook URL to send events")
        .type(ProviderConfigProperty.STRING_TYPE)
        .add()
        .property()
        .name("secret")
        .label("Secret")
        .secret(true)
        .required(false)
        .helpText("Shared secret used in payload signing")
        .type(ProviderConfigProperty.PASSWORD)
        .add()
        .property()
        .name("algorithm")
        .label("Algorithm")
        .options("HmacSHA256", "HmacSHA1")
        .defaultValue("HmacSHA256")
        .required(true)
        .helpText("Algorithm used in payload signing")
        .type(ProviderConfigProperty.LIST_TYPE)
        .add()
        .property()
        .name("eventTypes")
        .label("Event Types")
        .required(true)
        .helpText(
            "Event types that trigger this webhook. This can be a Java Pattern. Event types are prefixed with `access`/`admin`/`system` for different event categories. To select all types, use `*`. To select all types of a category use, e.g. `access.*`. To select a specific event type, use, e.g. `access.LOGIN`, `admin.USER-CREATE`. Admin event types are created from concatenating the resource type and the operation type.")
        .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE)
        .add()
        .build();
  }
}

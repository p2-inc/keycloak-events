package io.phasetwo.keycloak.ui;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.phasetwo.keycloak.webhooks.Webhooks.*;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
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

  @Override
  public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
    // Called after a component is created
    log.debugf("[realmId=%s] onCreate: %s", realm.getId(), componentModelToString(model));

    WebhookProvider webhooks = session.getProvider(WebhookProvider.class);
    WebhookModel w = webhooks.createWebhook(realm, model.get("url"));
    mergeWebhook(w, model);
  }

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
                              ComponentModel c = createComponentForWebhook(session, realm, w);
                              c = realm.addComponentModel(c);
                              log.debugf(
                                  "Added ComponentModel with id %s for webhook %s", c.getId(), w);
                              w.setComponentId(c.getId());
                            });
                  });
        });
  }

  @Override
  public void close() {}

  @Override
  public String getId() {
    return WEBHOOKS_ADMIN_UI_PROVIDER_ID;
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

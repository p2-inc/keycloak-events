package io.phasetwo.keycloak.resources;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.events.Version;
import io.phasetwo.keycloak.ext.util.Stats;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

@JBossLog
@AutoService(RealmResourceProviderFactory.class)
public class EventsResourceProviderFactory implements RealmResourceProviderFactory {

  public static final String ID = "events";
  public static final String ROLE_PUBLISH_EVENTS = "publish-events";

  @Override
  public RealmResourceProvider create(KeycloakSession session) {
    return new EventsResourceProvider(session);
  }

  @Override
  public void init(Config.Scope config) {
    Stats.collect(Version.getName(), Version.getVersion(), Version.getCommit());
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    factory.register(
        (ProviderEvent event) -> {
          if (event instanceof RealmModel.RealmPostCreateEvent) {
            realmPostCreate((RealmModel.RealmPostCreateEvent) event);
          } else if (event instanceof PostMigrationEvent) {
            KeycloakModelUtils.runJobInTransaction(factory, this::initRoles);
          }
        });
  }

  private void initRoles(KeycloakSession session) {
    RealmManager manager = new RealmManager(session);
    session
        .realms()
        .getRealmsStream()
        .forEach(
            realm -> {
              ClientModel client = realm.getMasterAdminClient();
              if (client.getRole(ROLE_PUBLISH_EVENTS) == null) {
                addMasterAdminRoles(manager, realm);
              }
              if (!realm.getName().equals(Config.getAdminRealm())) {
                client = realm.getClientByClientId(manager.getRealmAdminClientId(realm));
                if (client.getRole(ROLE_PUBLISH_EVENTS) == null) {
                  addRealmAdminRoles(manager, realm);
                }
              }
            });
  }

  private void realmPostCreate(RealmModel.RealmPostCreateEvent event) {
    RealmModel realm = event.getCreatedRealm();
    RealmManager manager = new RealmManager(event.getKeycloakSession());
    addMasterAdminRoles(manager, realm);
    if (!realm.getName().equals(Config.getAdminRealm())) addRealmAdminRoles(manager, realm);
  }

  private void addMasterAdminRoles(RealmManager manager, RealmModel realm) {
    RealmModel master = manager.getRealmByName(Config.getAdminRealm());
    RoleModel admin = master.getRole(AdminRoles.ADMIN);
    ClientModel client = realm.getMasterAdminClient();
    addRoles(client, admin);
  }

  private void addRealmAdminRoles(RealmManager manager, RealmModel realm) {
    ClientModel client = realm.getClientByClientId(manager.getRealmAdminClientId(realm));
    RoleModel admin = client.getRole(AdminRoles.REALM_ADMIN);
    addRoles(client, admin);
  }

  private void addRoles(ClientModel client, RoleModel parent) {
    String[] names = new String[] {ROLE_PUBLISH_EVENTS};
    for (String name : names) {
      RoleModel role = client.addRole(name);
      role.setDescription("${role_" + name + "}");
      parent.addCompositeRole(role);
    }
  }

  @Override
  public void close() {}

  @Override
  public String getId() {
    return ID;
  }
}

package org.keycloak.services.resources.admin.permissions;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.AdminAuth;

// I'm not sure if this is a good idea, but it seems to be the only way to get admin permissions
public class ManagementPermissions {
  public static boolean hasOneAdminRole(
      KeycloakSession session, RealmModel realm, AdminAuth auth, String... adminRoles) {
    return new MgmtPermissions(session, realm, auth).hasOneAdminRole(adminRoles);
  }
}

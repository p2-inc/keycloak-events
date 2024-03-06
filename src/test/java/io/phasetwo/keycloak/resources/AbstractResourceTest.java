package io.phasetwo.keycloak.resources;


import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.Keycloak;
import org.testcontainers.Testcontainers;

public abstract class AbstractResourceTest {

  public static final String KEYCLOAK_IMAGE =
      String.format(
          "quay.io/phasetwo/keycloak-crdb:%s", System.getProperty("keycloak-version", "23.0.0"));
  public static final String REALM = "master";
  public static final String ADMIN_CLI = "admin-cli";

  static final String[] deps = {
    "org.keycloak:keycloak-admin-client",
    "io.phasetwo.keycloak:keycloak-orgs",
    "com.github.xgp:kitchen-sink",
    "org.openjdk.nashorn:nashorn-core"
  };

  static List<File> getDeps() {
    List<File> dependencies = new ArrayList<File>();
    for (String dep : deps) {
      dependencies.addAll(getDep(dep));
    }
    return dependencies;
  }

  static List<File> getDep(String pkg) {
    return Maven.resolver()
        .loadPomFromFile("./pom.xml")
        .resolve(pkg)
        .withoutTransitivity()
        .asList(File.class);
  }

  public static Keycloak keycloak;

  public static final KeycloakContainer container =
      new KeycloakContainer(KEYCLOAK_IMAGE)
          .withContextPath("/auth")
          .withReuse(true)
          .withProviderClassesFrom("target/classes")
          .withProviderLibsFrom(getDeps())
          .withAccessToHost(true);

  protected static final int WEBHOOK_SERVER_PORT = 8083;

  static {
    container.start();
  }

  @BeforeAll
  public static void beforeAll() {
    Testcontainers.exposeHostPorts(WEBHOOK_SERVER_PORT);
    keycloak =
        getKeycloak(REALM, ADMIN_CLI, container.getAdminUsername(), container.getAdminPassword());
  }

  public static Keycloak getKeycloak(String realm, String clientId, String user, String pass) {
    return Keycloak.getInstance(getAuthUrl(), realm, user, pass, clientId);
  }

  public static String getAuthUrl() {
    return container.getAuthServerUrl();
  }
}

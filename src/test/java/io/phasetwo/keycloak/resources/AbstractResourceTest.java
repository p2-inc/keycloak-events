package io.phasetwo.keycloak.resources;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.Keycloak;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.MountableFile;

public abstract class AbstractResourceTest {

  public static final String KEYCLOAK_IMAGE =
      String.format(
          "quay.io/phasetwo/keycloak-crdb:%s", System.getProperty("keycloak-version", "26.5.7"));
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

  public static final KeycloakContainer container = initKeycloakContainer();

  private static KeycloakContainer initKeycloakContainer() {
    KeycloakContainer keycloakContainer = new KeycloakContainer(KEYCLOAK_IMAGE)
            .withContextPath("/auth")
            .withReuse(true)
            .withProviderClassesFrom("target/classes")
            .withProviderLibsFrom(getDeps())
            .withCustomCommand("--spi-events-listener-ext-event-webhook-store-webhook-events=true")
            .withAccessToHost(true);
    if (isJacocoPresent()) {
      keycloakContainer = keycloakContainer.withCopyFileToContainer(
                      MountableFile.forHostPath("target/jacoco-agent/"),
                      "/jacoco-agent"
              )
              .withEnv("JAVA_OPTS", "-XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m -javaagent:/jacoco-agent/org.jacoco.agent-runtime.jar=destfile=/tmp/jacoco.exec");
    }

    return keycloakContainer;
  }

  private static boolean isJacocoPresent() {
    return Files.exists(Path.of("target/jacoco-agent/org.jacoco.agent-runtime.jar"));
  }

  protected static final int WEBHOOK_SERVER_PORT = 8083;

  @BeforeAll
  public static void beforeAll() {
    container.start();
    Testcontainers.exposeHostPorts(WEBHOOK_SERVER_PORT);
    keycloak =
        getKeycloak(REALM, ADMIN_CLI, container.getAdminUsername(), container.getAdminPassword());
  }

  @AfterAll
  public static void tearDown() throws IOException {
    String containerId = container.getContainerId();
    String containerShortId;
    if (containerId.length() > 12) {
      containerShortId = containerId.substring(0, 12);
    } else {
      containerShortId = containerId;
    }
    container.getDockerClient().stopContainerCmd(containerId).exec();
    if (isJacocoPresent()) {
      Files.createDirectories(Path.of("target", "jacoco-report"));
      container.copyFileFromContainer("/tmp/jacoco.exec", "./target/jacoco-report/jacoco-%s.exec".formatted(containerShortId));
    }
    container.stop();
  }

  public static Keycloak getKeycloak(String realm, String clientId, String user, String pass) {
    return Keycloak.getInstance(getAuthUrl(), realm, user, pass, clientId);
  }

  public static String getAuthUrl() {
    return container.getAuthServerUrl();
  }
}

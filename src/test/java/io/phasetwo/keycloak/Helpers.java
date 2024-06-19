package io.phasetwo.keycloak;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URLEncoder;
import java.util.List;
import java.util.Set;
import org.apache.http.impl.client.CloseableHttpClient;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class Helpers {

  public static RealmEventsConfigRepresentation addEventListener(
      Keycloak keycloak, String realm, String name) {
    RealmResource realmResource = keycloak.realm(realm);
    RealmEventsConfigRepresentation eventsConfig = realmResource.getRealmEventsConfig();
    if (eventsConfig.getEventsListeners().contains(name)) return eventsConfig; // disallow dupes
    eventsConfig.setEventsListeners(
        new ImmutableList.Builder<String>()
            .addAll(eventsConfig.getEventsListeners())
            .add(name)
            .build());
    realmResource.updateRealmEventsConfig(eventsConfig);
    return eventsConfig;
  }

  public static RealmEventsConfigRepresentation removeEventListener(
      Keycloak keycloak, String realm, String name) {
    RealmResource realmResource = keycloak.realm(realm);
    RealmEventsConfigRepresentation eventsConfig = realmResource.getRealmEventsConfig();
    if (eventsConfig.getEventsListeners().contains(name)) {
      List<String> evs = Lists.newArrayList(eventsConfig.getEventsListeners());
      evs.remove(name);
      eventsConfig.setEventsListeners(evs);
      realmResource.updateRealmEventsConfig(eventsConfig);
    }
    return eventsConfig;
  }

  public static UserRepresentation createUser(Keycloak keycloak, String realm, String username) {
    UserRepresentation user = new UserRepresentation();
    user.setEnabled(true);
    user.setUsername(username);
    keycloak.realm(realm).users().create(user);
    return user;
  }

  public static String createWebhook(
      Keycloak keycloak,
      CloseableHttpClient httpClient,
      String baseUrl,
      String url,
      String secret,
      Set<String> types)
      throws Exception {
    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setEnabled(true);
    rep.setUrl(url);
    rep.setSecret(secret);
    if (types == null) {
      rep.setEventTypes(ImmutableSet.of("*"));
    } else {
      rep.setEventTypes(types);
    }

    LegacySimpleHttp.Response response =
        LegacySimpleHttp.doPost(baseUrl, httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(201));
    assertNotNull(response.getFirstHeader("Location"));
    String loc = response.getFirstHeader("Location");
    String id = loc.substring(loc.lastIndexOf("/") + 1);
    return id;
  }

  public static String urlencode(String u) {
    try {
      return URLEncoder.encode(u, "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }

  public static int nextFreePort(int from, int to) {
    for (int port = from; port <= to; port++) {
      if (isLocalPortFree(port)) {
        return port;
      }
    }
    throw new IllegalStateException("No free port found");
  }

  private static boolean isLocalPortFree(int port) {
    try {
      new ServerSocket(port).close();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}

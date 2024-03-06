package io.phasetwo.keycloak.resources;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.phasetwo.keycloak.representation.RealmAttributeRepresentation;
import java.net.URLEncoder;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.keycloak.broker.provider.util.SimpleHttp;

@JBossLog
public class RealmAttributesResourceTest extends AbstractResourceTest {

  CloseableHttpClient httpClient = HttpClients.createDefault();

  String baseUrl() {
    return getAuthUrl() + "/realms/master/attributes";
  }

  String urlencode(String u) {
    try {
      return URLEncoder.encode(u, "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }

  @Test
  public void testGetAttributes() throws Exception {
    SimpleHttp.Response response =
        SimpleHttp.doGet(baseUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    Map<String, RealmAttributeRepresentation> attributes =
        response.asJson(new TypeReference<Map<String, RealmAttributeRepresentation>>() {});
    assertNotNull(attributes);
    assertTrue(attributes.size() > 0);
  }

  @Test
  public void testAddGetAttribute() throws Exception {

    String key = "_providerConfig.ext-event-script.0";
    String value =
        "{ \"scriptCode\": \"function onEvent(event) {\\\n  LOG.info(event.type + \\\" in realm \\\" + realm.name + \\\" for user \\\" + user.username);\\\n}\\\n\\\nfunction onAdminEvent(event, representation) {\\\n  LOG.info(event.operationType + \\\" on \\\" + event.resourceType + \\\" in realm \\\" + realm.name);\\\n}\\\n\", \"scriptName\": \"test-debug\", \"scriptDescription\": \"debugger output\" }";

    RealmAttributeRepresentation rep = new RealmAttributeRepresentation();
    rep.setRealm("master");
    rep.setName(key);
    rep.setValue(value);
    SimpleHttp.Response response =
        SimpleHttp.doPost(baseUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(201));
    assertThat(response.getFirstHeader("Location"), containsString(key));

    response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(key), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(200));
    rep = response.asJson(new TypeReference<RealmAttributeRepresentation>() {});
    assertNotNull(rep);
    assertThat(rep.getRealm(), is("master"));
    assertThat(rep.getName(), is(key));
    assertThat(rep.getValue(), is(value));
  }

  @Test
  public void testUpdateGetAttribute() throws Exception {
    String key = "_providerConfig.test.1";
    String value0 = "foo";
    String value1 = "bar";

    RealmAttributeRepresentation rep = new RealmAttributeRepresentation();
    rep.setRealm("master");
    rep.setName(key);
    rep.setValue(value0);
    SimpleHttp.Response response =
        SimpleHttp.doPost(baseUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(201));
    assertThat(response.getFirstHeader("Location"), containsString(key));

    response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(key), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(200));
    rep = response.asJson(new TypeReference<RealmAttributeRepresentation>() {});
    assertNotNull(rep);
    assertThat(rep.getRealm(), is("master"));
    assertThat(rep.getName(), is(key));
    assertThat(rep.getValue(), is(value0));

    rep.setValue(value1);
    response =
        SimpleHttp.doPut(baseUrl() + "/" + urlencode(key), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(204));

    response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(key), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(200));
    rep = response.asJson(new TypeReference<RealmAttributeRepresentation>() {});
    assertNotNull(rep);
    assertThat(rep.getRealm(), is("master"));
    assertThat(rep.getName(), is(key));
    assertThat(rep.getValue(), is(value1));
  }

  @Test
  public void testRemoveAttribute() throws Exception {
    String key = "_providerConfig.test.2";
    String value0 = "foo";

    RealmAttributeRepresentation rep = new RealmAttributeRepresentation();
    rep.setRealm("master");
    rep.setName(key);
    rep.setValue(value0);
    SimpleHttp.Response response =
        SimpleHttp.doPost(baseUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(201));
    assertThat(response.getFirstHeader("Location"), containsString(key));

    response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(key), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(200));
    rep = response.asJson(new TypeReference<RealmAttributeRepresentation>() {});
    assertNotNull(rep);
    assertThat(rep.getRealm(), is("master"));
    assertThat(rep.getName(), is(key));
    assertThat(rep.getValue(), is(value0));

    response =
        SimpleHttp.doDelete(baseUrl() + "/" + urlencode(key), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(204));

    response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(key), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(404));
  }
}

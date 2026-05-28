package io.phasetwo.keycloak.events;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class FlatWebhookTest {

  @Test
  public void fullyPopulatedWebhookMapsAllFields() {
    FlatWebhook flat =
        new FlatWebhook(
            "ADMIN",
            "event-uuid",
            "webhook-uuid",
            "send-uuid",
            202,
            3,
            1700000000000L,
            "{\"type\":\"admin.USER-CREATE\"}");

    Map<String, Object> map = flat.toMap();

    assertThat(map.get("eventType"), equalTo("ADMIN"));
    assertThat(map.get("eventId"), equalTo("event-uuid"));
    assertThat(map.get("webhookId"), equalTo("webhook-uuid"));
    assertThat(map.get("sendId"), equalTo("send-uuid"));
    assertThat(map.get("status"), equalTo(202));
    assertThat(map.get("retryNum"), equalTo(3));
    assertThat(map.get("sentAt"), equalTo(1700000000000L));
    assertThat(map.get("rawPayload"), equalTo("{\"type\":\"admin.USER-CREATE\"}"));
  }

  @Test
  public void nullStringFieldsAreOmittedNumericFieldsRetained() {
    FlatWebhook flat = new FlatWebhook(null, null, null, null, 0, 1, 1700000000000L, null);

    Map<String, Object> map = flat.toMap();

    assertThat(map.get("eventType"), is(nullValue()));
    assertThat(map.get("eventId"), is(nullValue()));
    assertThat(map.get("webhookId"), is(nullValue()));
    assertThat(map.get("sendId"), is(nullValue()));
    assertThat(map.get("rawPayload"), is(nullValue()));
    assertThat(map.containsKey("eventType"), is(false));
    assertThat(map.containsKey("rawPayload"), is(false));
    assertThat(map.get("status"), equalTo(0));
    assertThat(map.get("retryNum"), equalTo(1));
    assertThat(map.get("sentAt"), equalTo(1700000000000L));
  }

  @Test
  public void toMapKeysSurviveLogContextRoundTrip() {
    FlatWebhook flat =
        new FlatWebhook(
            "USER", "e-1", "w-1", "s-1", 500, 1, 1700000000000L, "{\"type\":\"LOGIN\"}");

    Map<String, Object> map = flat.toMap();
    assertThat(map.size(), is(not(0)));
    assertThat(map.containsKey("status"), is(true));
    assertThat(map.containsKey("retryNum"), is(true));
    assertThat(map.containsKey("sentAt"), is(true));
  }
}

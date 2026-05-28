package io.phasetwo.keycloak.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class FlatWebhook {

  @JsonProperty("eventType")
  private final String eventType;

  @JsonProperty("eventId")
  private final String eventId;

  @JsonProperty("webhookId")
  private final String webhookId;

  @JsonProperty("sendId")
  private final String sendId;

  @JsonProperty("status")
  private final int status;

  @JsonProperty("retryNum")
  private final int retryNum;

  @JsonProperty("sentAt")
  private final long sentAt;

  @JsonProperty("rawPayload")
  private final String rawPayload;

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    FlatEvents.putIfNotNull(map, "eventType", eventType);
    FlatEvents.putIfNotNull(map, "eventId", eventId);
    FlatEvents.putIfNotNull(map, "webhookId", webhookId);
    FlatEvents.putIfNotNull(map, "sendId", sendId);
    map.put("status", status);
    map.put("retryNum", retryNum);
    map.put("sentAt", sentAt);
    FlatEvents.putIfNotNull(map, "rawPayload", rawPayload);
    return map;
  }
}

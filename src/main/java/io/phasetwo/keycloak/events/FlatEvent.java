package io.phasetwo.keycloak.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.keycloak.events.Event;

@Data
public class FlatEvent {

  public static final String CLASS = "USER";

  @JsonProperty("class")
  private final String eventClass = CLASS;

  @JsonProperty("id")
  private final String id;

  @JsonProperty("type")
  private final String type;

  @JsonProperty("realmId")
  private final String realmId;

  @JsonProperty("realmName")
  private final String realmName;

  @JsonProperty("clientId")
  private final String clientId;

  @JsonProperty("userId")
  private final String userId;

  @JsonProperty("sessionId")
  private final String sessionId;

  @JsonProperty("ipAddress")
  private final String ipAddress;

  @JsonProperty("error")
  private final String error;

  @JsonProperty("time")
  private final long time;

  @JsonProperty("detailsJson")
  private final String detailsJson;

  public FlatEvent(Event event) {
    this.id = event.getId();
    this.type = event.getType() != null ? event.getType().name() : null;
    this.realmId = event.getRealmId();
    this.realmName = event.getRealmName();
    this.clientId = event.getClientId();
    this.userId = event.getUserId();
    this.sessionId = event.getSessionId();
    this.ipAddress = event.getIpAddress();
    this.error = event.getError();
    this.time = event.getTime();
    this.detailsJson = FlatEvents.serializeDetails(event.getDetails());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("class", eventClass);
    putIfNotNull(map, "id", id);
    putIfNotNull(map, "type", type);
    putIfNotNull(map, "realmId", realmId);
    putIfNotNull(map, "realmName", realmName);
    putIfNotNull(map, "clientId", clientId);
    putIfNotNull(map, "userId", userId);
    putIfNotNull(map, "sessionId", sessionId);
    putIfNotNull(map, "ipAddress", ipAddress);
    putIfNotNull(map, "error", error);
    map.put("time", time);
    putIfNotNull(map, "detailsJson", detailsJson);
    return map;
  }

  private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}

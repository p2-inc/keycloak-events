package io.phasetwo.keycloak.events.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.keycloak.events.Event;

@Data
public class FlatEvent {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("type")
  private final String type;

  @JsonProperty("realmid")
  private final String realmId;

  @JsonProperty("realmname")
  private final String realmName;

  @JsonProperty("clientid")
  private final String clientId;

  @JsonProperty("userid")
  private final String userId;

  @JsonProperty("sessionid")
  private final String sessionId;

  @JsonProperty("ipaddress")
  private final String ipAddress;

  @JsonProperty("error")
  private final String error;

  @JsonProperty("time")
  private final long time;

  @JsonProperty("detailsjson")
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
}

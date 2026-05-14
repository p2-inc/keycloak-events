package io.phasetwo.keycloak.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;

@Data
public class FlatAdminEvent {

  public static final String CLASS = "ADMIN";

  @JsonProperty("class")
  private final String eventClass = CLASS;

  @JsonProperty("id")
  private final String id;

  @JsonProperty("time")
  private final long time;

  @JsonProperty("realmId")
  private final String realmId;

  @JsonProperty("realmName")
  private final String realmName;

  @JsonProperty("operationType")
  private final String operationType;

  @JsonProperty("resourceType")
  private final String resourceType;

  @JsonProperty("resourcePath")
  private final String resourcePath;

  @JsonProperty("representation")
  private final String representation;

  @JsonProperty("error")
  private final String error;

  @JsonProperty("authRealmId")
  private final String authRealmId;

  @JsonProperty("authRealmName")
  private final String authRealmName;

  @JsonProperty("authClientId")
  private final String authClientId;

  @JsonProperty("authUserId")
  private final String authUserId;

  @JsonProperty("authIpAddress")
  private final String authIpAddress;

  @JsonProperty("detailsJson")
  private final String detailsJson;

  public FlatAdminEvent(AdminEvent adminEvent) {
    this.id = adminEvent.getId();
    this.time = adminEvent.getTime();
    this.realmId = adminEvent.getRealmId();
    this.realmName = adminEvent.getRealmName();
    this.operationType =
        adminEvent.getOperationType() != null ? adminEvent.getOperationType().name() : null;
    this.resourceType =
        adminEvent.getResourceType() != null ? adminEvent.getResourceType().name() : null;
    this.resourcePath = adminEvent.getResourcePath();
    this.representation = adminEvent.getRepresentation();
    this.error = adminEvent.getError();

    AuthDetails authDetails = adminEvent.getAuthDetails();
    if (authDetails != null) {
      this.authRealmId = authDetails.getRealmId();
      this.authRealmName = authDetails.getRealmName();
      this.authClientId = authDetails.getClientId();
      this.authUserId = authDetails.getUserId();
      this.authIpAddress = authDetails.getIpAddress();
    } else {
      this.authRealmId = null;
      this.authRealmName = null;
      this.authClientId = null;
      this.authUserId = null;
      this.authIpAddress = null;
    }

    this.detailsJson = FlatEvents.serializeDetails(adminEvent.getDetails());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("class", eventClass);
    FlatEvents.putIfNotNull(map, "id", id);
    map.put("time", time);
    FlatEvents.putIfNotNull(map, "realmId", realmId);
    FlatEvents.putIfNotNull(map, "realmName", realmName);
    FlatEvents.putIfNotNull(map, "operationType", operationType);
    FlatEvents.putIfNotNull(map, "resourceType", resourceType);
    FlatEvents.putIfNotNull(map, "resourcePath", resourcePath);
    FlatEvents.putIfNotNull(map, "representation", representation);
    FlatEvents.putIfNotNull(map, "error", error);
    FlatEvents.putIfNotNull(map, "authRealmId", authRealmId);
    FlatEvents.putIfNotNull(map, "authRealmName", authRealmName);
    FlatEvents.putIfNotNull(map, "authClientId", authClientId);
    FlatEvents.putIfNotNull(map, "authUserId", authUserId);
    FlatEvents.putIfNotNull(map, "authIpAddress", authIpAddress);
    FlatEvents.putIfNotNull(map, "detailsJson", detailsJson);
    return map;
  }
}

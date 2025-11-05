package io.phasetwo.keycloak.events.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;

@Data
public class FlatAdminEvent {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("time")
  private final long time;

  @JsonProperty("realmid")
  private final String realmId;

  @JsonProperty("realmname")
  private final String realmName;

  @JsonProperty("operationtype")
  private final String operationType;

  @JsonProperty("resourcetype")
  private final String resourceType;

  @JsonProperty("resourcepath")
  private final String resourcePath;

  @JsonProperty("representation")
  private final String representation;

  @JsonProperty("error")
  private final String error;

  @JsonProperty("authrealmid")
  private final String authRealmId;

  @JsonProperty("authrealmname")
  private final String authRealmName;

  @JsonProperty("authclientid")
  private final String authClientId;

  @JsonProperty("authuserid")
  private final String authUserId;

  @JsonProperty("authipaddress")
  private final String authIpAddress;

  @JsonProperty("detailsjson")
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
}

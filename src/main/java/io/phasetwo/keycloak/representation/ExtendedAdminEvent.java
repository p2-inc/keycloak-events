package io.phasetwo.keycloak.representation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.models.RealmModel;

/** Unified event class. */
public class ExtendedAdminEvent extends AdminEvent {

  @JsonIgnore private ExtendedAuthDetails extAuthDetails;
  private String uid;
  private String type;
  private Map<String, String> details = new HashMap<>();

  private static String createType(AdminEvent event) {
    StringBuilder o = new StringBuilder("admin.");
    if (event.getResourceType() != null) o.append(event.getResourceType());
    if (event.getResourceType() != null && event.getOperationType() != null) o.append("-");
    if (event.getOperationType() != null) o.append(event.getOperationType());
    return o.toString();
  }

  private static String createType(Event event) {
    StringBuilder o = new StringBuilder("access.");
    if (event.getType() != null) o.append(event.getType());
    return o.toString();
  }

  public ExtendedAdminEvent() {}

  public ExtendedAdminEvent(String uid, AdminEvent event, RealmModel realm) {
    this.uid = uid;
    this.type = createType(event);

    setTime(event.getTime());
    setRealmId(realm.getName());
    setAuthDetails(event.getAuthDetails());
    extAuthDetails.setRealmId(realm.getName());
    setResourceType(event.getResourceType());
    setOperationType(event.getOperationType());
    setResourcePath(event.getResourcePath());
    setRepresentation(event.getRepresentation());
    setError(event.getError());
  }

  public ExtendedAdminEvent(String uid, Event event, RealmModel realm) {
    this.uid = uid;
    this.type = createType(event);

    ExtendedAuthDetails authDetails = new ExtendedAuthDetails(null);
    authDetails.setRealmId(realm.getName());
    authDetails.setClientId(event.getClientId());
    authDetails.setIpAddress(event.getIpAddress());
    authDetails.setSessionId(event.getSessionId());
    authDetails.setUserId(event.getUserId());
    setAuthDetails(authDetails);
    setDetails(event.getDetails());
    setError(event.getError());
    setRealmId(event.getRealmId());
    setTime(event.getTime());
  }

  public String getUid() {
    return uid;
  }

  public void setUid(String uid) {
    this.uid = uid;
  }

  @JsonProperty("type")
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @JsonProperty("details")
  public Map<String, String> getDetails() {
    return details;
  }

  public void setDetails(Map<String, String> details) {
    this.details = details;
  }

  @JsonProperty("authDetails")
  @Override
  public ExtendedAuthDetails getAuthDetails() {
    return extAuthDetails;
  }

  @Override
  public void setAuthDetails(AuthDetails authDetails) {
    if (authDetails == null) {
      this.extAuthDetails = null;
    } else if (authDetails instanceof ExtendedAuthDetails) {
      this.extAuthDetails = (ExtendedAuthDetails) authDetails;
    } else {
      this.extAuthDetails = new ExtendedAuthDetails(authDetails);
    }
  }

  @Override
  @JsonIgnore
  public String getResourceTypeAsString() {
    return super.getResourceTypeAsString();
  }
}

package io.phasetwo.keycloak.representation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import java.util.Map;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.RealmModel;

/** Unified event class. */
public class ExtendedAdminEvent extends AdminEvent {

  @JsonIgnore private EventType nativeType;
  @JsonIgnore private ExtendedAuthDetails extAuthDetails;
  private String uid;
  private String type;
  private final Map<String, String> details;

  private static String createType(AdminEvent event) {
    StringBuilder o = new StringBuilder("admin.");
    if (event.getResourceTypeAsString() != null) {
      o.append(event.getResourceTypeAsString());
    }
    if (event.getResourceTypeAsString() != null && event.getOperationType() != null) o.append("-");
    if (event.getOperationType() != null) o.append(event.getOperationType());
    return o.toString();
  }

  private static String createType(Event event) {
    StringBuilder o = new StringBuilder("access.");
    if (event.getType() != null) o.append(event.getType());
    return o.toString();
  }

  public ExtendedAdminEvent() {
    this.details = Maps.newHashMap();
  }

  public ExtendedAdminEvent(
      String uid, AdminEvent event, RealmModel eventRealm, RealmModel authRealm) {
    this.uid = uid;
    this.type = createType(event);
    this.details = Maps.newHashMap();

    setTime(event.getTime());
    setRealmId(eventRealm.getId());
    setRealmName(eventRealm.getName());
    setAuthDetails(event.getAuthDetails());
    extAuthDetails.setRealmId(authRealm.getName());
    addDetails(event.getDetails());
    setResourceType(event.getResourceType());
    setResourceTypeAsString(event.getResourceTypeAsString());
    setOperationType(event.getOperationType());
    setResourcePath(event.getResourcePath());
    setRepresentation(event.getRepresentation());
    setError(event.getError());
    setId(event.getId());
  }

  public ExtendedAdminEvent(String uid, Event event, RealmModel realm) {
    this.uid = uid;
    this.nativeType = event.getType();
    this.type = createType(event);
    this.details = Maps.newHashMap();

    ExtendedAuthDetails authDetails = new ExtendedAuthDetails(null);
    authDetails.setRealmId(realm.getName());
    authDetails.setClientId(event.getClientId());
    authDetails.setIpAddress(event.getIpAddress());
    authDetails.setSessionId(event.getSessionId());
    authDetails.setUserId(event.getUserId());
    setAuthDetails(authDetails);
    addDetails(event.getDetails());
    setError(event.getError());
    setRealmId(event.getRealmId());
    setRealmName(event.getRealmName());
    setTime(event.getTime());
    setId(event.getId());
  }

  public String getUid() {
    return uid;
  }

  public void setUid(String uid) {
    this.uid = uid;
  }

  @JsonIgnore
  public EventType getNativeType() {
    return this.nativeType;
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
    this.details.clear();
    addDetails(details);
  }

  @JsonIgnore
  public void addDetails(Map<String, String> details) {
    if (details != null) this.details.putAll(details);
  }

  @JsonProperty("authDetails")
  @Override
  public ExtendedAuthDetails getAuthDetails() {
    return extAuthDetails;
  }

  @Override
  @JsonIgnore
  public void setAuthDetails(AuthDetails authDetails) {
    if (authDetails == null) {
      this.extAuthDetails = null;
    } else if (authDetails instanceof ExtendedAuthDetails) {
      this.extAuthDetails = (ExtendedAuthDetails) authDetails;
    } else {
      this.extAuthDetails = new ExtendedAuthDetails(authDetails);
    }
  }

  public void setAuthDetails(ExtendedAuthDetails authDetails) {
    this.extAuthDetails = authDetails;
  }

  @Override
  @JsonProperty("resourceType")
  public String getResourceTypeAsString() {
    return super.getResourceTypeAsString();
  }

  @Override
  @JsonIgnore
  public ResourceType getResourceType() {
    return super.getResourceType();
  }

  @Override
  @JsonProperty(value = "resourceId", access = JsonProperty.Access.READ_ONLY)
  public String getResourceId() {
    return super.getResourceId();
  }
}

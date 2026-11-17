package io.phasetwo.keycloak.events.format;

import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import java.time.Instant;
import java.util.Map;

/**
 * A flat, read-only view of an {@link ExtendedAdminEvent} for formatters, hiding the differences
 * between user ({@code access.*}), admin ({@code admin.*}) and custom ({@code system.*}) events.
 */
public final class EventView {

  public static final String USER_PREFIX = "access.";
  public static final String ADMIN_PREFIX = "admin.";

  private final ExtendedAdminEvent event;

  private EventView(ExtendedAdminEvent event) {
    this.event = event;
  }

  public static EventView of(ExtendedAdminEvent event) {
    return new EventView(event);
  }

  public ExtendedAdminEvent event() {
    return event;
  }

  /** A Keycloak user event (login, logout, token refresh...). */
  public boolean isUserEvent() {
    return type().startsWith(USER_PREFIX);
  }

  /** A Keycloak admin event (a REST API change). */
  public boolean isAdminEvent() {
    return type().startsWith(ADMIN_PREFIX);
  }

  /** {@code user}, {@code admin} or the custom event prefix (e.g. {@code system}). */
  public String category() {
    if (isUserEvent()) return "user";
    if (isAdminEvent()) return "admin";
    String t = type();
    int i = t.indexOf('.');
    return i > 0 ? t.substring(0, i) : "unknown";
  }

  /** The keycloak-events type, e.g. {@code access.LOGIN} or {@code admin.USER-CREATE}. */
  public String type() {
    return event.getType() == null ? "" : event.getType();
  }

  /**
   * The type as Keycloak itself names it: the user event type (e.g. {@code LOGIN}) for user events,
   * the operation type (e.g. {@code CREATE}) for admin events, and the type without its prefix
   * otherwise.
   */
  public String nativeType() {
    if (isAdminEvent() && event.getOperationType() != null) {
      return event.getOperationType().toString();
    }
    String t = type();
    int i = t.indexOf('.');
    return i >= 0 ? t.substring(i + 1) : t;
  }

  public String id() {
    return event.getId();
  }

  /** The per-send unique id assigned by the webhook sender. */
  public String uid() {
    return event.getUid();
  }

  public long timeMillis() {
    return event.getTime();
  }

  public Instant timestamp() {
    return Instant.ofEpochMilli(event.getTime());
  }

  public String realmId() {
    return event.getRealmId();
  }

  public String realmName() {
    return event.getRealmName();
  }

  private ExtendedAuthDetails auth() {
    return event.getAuthDetails();
  }

  /** The client the event came through (for admin events: the client the admin used). */
  public String clientId() {
    return auth() == null ? null : auth().getClientId();
  }

  /** The user the event is about (for admin events: the admin who made the change). */
  public String userId() {
    return auth() == null ? null : auth().getUserId();
  }

  public String username() {
    String u = auth() == null ? null : auth().getUsername();
    if (u == null && isUserEvent()) u = details().get("username");
    return u;
  }

  public String sessionId() {
    return auth() == null ? null : auth().getSessionId();
  }

  public String ipAddress() {
    return auth() == null ? null : auth().getIpAddress();
  }

  /** For admin events, the name of the realm the admin authenticated in. */
  public String authRealm() {
    return auth() == null ? null : auth().getRealmId();
  }

  public String error() {
    return event.getError();
  }

  public boolean isError() {
    return event.getError() != null && !event.getError().isEmpty();
  }

  public String resourceType() {
    return event.getResourceTypeAsString();
  }

  public String resourcePath() {
    return event.getResourcePath();
  }

  public String representation() {
    return event.getRepresentation();
  }

  public Map<String, String> details() {
    return event.getDetails() == null ? Map.of() : event.getDetails();
  }
}

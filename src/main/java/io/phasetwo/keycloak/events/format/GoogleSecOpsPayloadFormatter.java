package io.phasetwo.keycloak.events.format;

import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.keycloak.util.JsonSerialization;

/**
 * Formats events for a Google Security Operations (Chronicle) webhook feed with the {@code
 * KEYCLOAK} log type, so the default Keycloak parser maps them to UDM.
 *
 * <p>The default parser reads Keycloak's event tables as exported by Google Datastream: a change
 * record with {@code uuid}, {@code source_timestamp}, {@code object}, {@code read_method}, {@code
 * source_metadata} and a {@code payload} holding the row with snake_case column names ({@code
 * type}, {@code user_id}, {@code session_id}, {@code ip_address}, {@code details_json}, ...). This
 * formatter emits one such record per event, using the plain Keycloak event type (e.g. {@code
 * LOGIN}) that the parser matches on to classify events (e.g. {@code USER_LOGIN}).
 *
 * <p>Send to the feed's {@code ...:importPushLogs} endpoint with authType {@code headers} and the
 * {@code header.x-goog-chronicle-auth} (API key) and {@code header.x-chronicle-auth} (feed secret)
 * config entries.
 */
public class GoogleSecOpsPayloadFormatter implements WebhookPayloadFormatter {

  static final String READ_METHOD = "keycloak-events-webhook";
  static final String SCHEMA = "keycloak";
  static final String USER_EVENT_TABLE = "event_entity";
  static final String ADMIN_EVENT_TABLE = "admin_event_entity";

  /**
   * Invalid payloads, bad credentials and oversized requests won't succeed on retry. 429 and 5xx
   * will.
   */
  static final Set<Integer> NON_RETRYABLE = Set.of(400, 401, 403, 404, 413);

  @Override
  public FormattedPayload format(List<ExtendedAdminEvent> events, Map<String, String> config)
      throws IOException {
    if (events.size() != 1) {
      throw new IllegalArgumentException("google-secops sends one event per request");
    }
    return FormattedPayload.json(
        JsonSerialization.writeValueAsString(record(new EventView(events.get(0)), Instant.now())));
  }

  @Override
  public boolean isRetryable(int httpStatus) {
    return !NON_RETRYABLE.contains(httpStatus);
  }

  Map<String, Object> record(EventView v, Instant readAt) throws IOException {
    String table = v.isAdminEvent() ? ADMIN_EVENT_TABLE : USER_EVENT_TABLE;
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("uuid", v.uid() != null && !v.uid().isEmpty() ? v.uid() : v.id());
    r.put("read_timestamp", iso(readAt));
    r.put("source_timestamp", iso(v.timestamp()));
    r.put("object", SCHEMA + "_" + table);
    r.put("read_method", READ_METHOD);

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("schema", SCHEMA);
    meta.put("table", table);
    meta.put("is_deleted", false);
    meta.put("change_type", "INSERT");
    r.put("source_metadata", meta);

    r.put("payload", v.isAdminEvent() ? adminRow(v) : userRow(v));
    return r;
  }

  /** A row of Keycloak's EVENT_ENTITY table. */
  Map<String, Object> userRow(EventView v) throws IOException {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("id", v.id());
    p.put("type", v.nativeType());
    p.put("realm_id", v.realmId());
    p.put("client_id", v.clientId());
    p.put("user_id", v.userId());
    p.put("session_id", v.sessionId());
    p.put("ip_address", v.ipAddress());
    p.put("error", v.error());
    p.put("event_time", v.timeMillis());
    Map<String, String> details = new LinkedHashMap<>(v.details());
    // the parser takes the target user from details_json.username
    if (!details.containsKey("username") && v.username() != null) {
      details.put("username", v.username());
    }
    p.put("details_json", JsonSerialization.writeValueAsString(details));
    return p;
  }

  /**
   * A row of Keycloak's ADMIN_EVENT_ENTITY table, plus a {@code type} of {@code
   * <RESOURCE>-<OPERATION>} (e.g. {@code USER-CREATE}) so the parser records a product event type.
   */
  Map<String, Object> adminRow(EventView v) throws IOException {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("id", v.id());
    p.put("type", adminType(v));
    p.put("admin_event_time", v.timeMillis());
    p.put("realm_id", v.realmId());
    p.put("operation_type", v.nativeType());
    p.put("resource_type", v.resourceType());
    p.put("resource_path", v.resourcePath());
    p.put("auth_realm_id", v.authRealm());
    p.put("auth_client_id", v.clientId());
    p.put("auth_user_id", v.userId());
    p.put("ip_address", v.ipAddress());
    p.put("error", v.error());
    p.put("representation", v.representation());
    p.put("details_json", JsonSerialization.writeValueAsString(v.details()));
    return p;
  }

  String adminType(EventView v) {
    String t = v.type();
    return t.startsWith(EventView.ADMIN_PREFIX) ? t.substring(EventView.ADMIN_PREFIX.length()) : t;
  }

  String iso(Instant i) {
    return DateTimeFormatter.ISO_INSTANT.format(i.truncatedTo(ChronoUnit.MILLIS));
  }
}

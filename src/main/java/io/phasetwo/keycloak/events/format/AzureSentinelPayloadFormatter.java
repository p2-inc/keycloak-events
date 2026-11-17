package io.phasetwo.keycloak.events.format;

import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.util.JsonSerialization;

/**
 * Formats events for Microsoft Sentinel through the Azure Monitor Logs Ingestion API ({@code POST
 * {endpoint}/dataCollectionRules/{dcrImmutableId}/streams/{stream}?api-version=2023-01-01}),
 * authenticated with authType {@code oauth2-client-credentials} against Microsoft Entra (scope
 * {@code https://monitor.azure.com//.default}).
 *
 * <p>For admin events, {@code UserId}/{@code Username} are the acting admin and {@code
 * TargetUserId}/{@code TargetUsername} the affected user (for operations on users).
 *
 * <p>The body is a JSON array of flat rows whose columns are {@link #COLUMNS}. Declare the same
 * columns on the data collection rule's stream and on the custom table (e.g. {@code
 * KeycloakEvents_CL}). Column names follow Log Analytics rules (letter first, at most 45
 * characters, no reserved names such as {@code id} or {@code Type}).
 */
public class AzureSentinelPayloadFormatter implements WebhookPayloadFormatter {

  /** A column of the stream declaration: name and Log Analytics type. */
  /**
   * A column of the stream declaration.
   *
   * @param name the column name
   * @param type the Log Analytics type
   */
  public record Column(String name, String type) {}

  /** The stream columns, in order. */
  public static final List<Column> COLUMNS =
      List.of(
          new Column("TimeGenerated", "datetime"),
          new Column("EventId", "string"),
          new Column("EventUid", "string"),
          new Column("EventCategory", "string"),
          new Column("EventType", "string"),
          new Column("EventTypeFull", "string"),
          new Column("EventResult", "string"),
          new Column("RealmId", "string"),
          new Column("RealmName", "string"),
          new Column("ClientId", "string"),
          new Column("UserId", "string"),
          new Column("Username", "string"),
          new Column("SessionId", "string"),
          new Column("IpAddress", "string"),
          new Column("Error", "string"),
          new Column("AuthRealm", "string"),
          new Column("OperationType", "string"),
          new Column("ResourceType", "string"),
          new Column("ResourcePath", "string"),
          new Column("TargetUserId", "string"),
          new Column("TargetUsername", "string"),
          new Column("Representation", "string"),
          new Column("Details", "dynamic"));

  /** Log Analytics truncates string fields over 64 KB; keep representations well under that. */
  static final int MAX_FIELD_CHARS = 32 * 1024;

  /** The Logs Ingestion API accepts at most 1 MB per call. */
  static final int MAX_BODY_BYTES = 1024 * 1024;

  /**
   * Bad payloads, missing permissions (403), unknown DCR/stream (404) and oversized calls (413)
   * won't succeed on retry. 401 is retried with a freshly fetched token.
   */
  static final Set<Integer> NON_RETRYABLE = Set.of(400, 403, 404, 413);

  @Override
  public FormattedPayload format(List<ExtendedAdminEvent> events, Map<String, String> config)
      throws IOException {
    List<Map<String, Object>> rows = new ArrayList<>(events.size());
    for (ExtendedAdminEvent e : events) rows.add(row(new EventView(e)));
    String body = JsonSerialization.writeValueAsString(rows);
    if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
      // only possible with huge details; drop them rather than have the call rejected
      for (Map<String, Object> row : rows) row.remove("Details");
      body = JsonSerialization.writeValueAsString(rows);
    }
    return FormattedPayload.json(body);
  }

  @Override
  public boolean isRetryable(int httpStatus) {
    return !NON_RETRYABLE.contains(httpStatus);
  }

  Map<String, Object> row(EventView v) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put(
        "TimeGenerated",
        DateTimeFormatter.ISO_INSTANT.format(v.timestamp().truncatedTo(ChronoUnit.MILLIS)));
    r.put("EventId", v.id());
    put(r, "EventUid", v.uid());
    r.put("EventCategory", v.category());
    r.put("EventType", v.nativeType());
    r.put("EventTypeFull", v.type());
    r.put("EventResult", v.isError() ? "Failure" : "Success");
    put(r, "RealmId", v.realmId());
    put(r, "RealmName", v.realmName());
    put(r, "ClientId", v.clientId());
    put(r, "UserId", v.userId());
    put(r, "Username", v.username());
    put(r, "SessionId", v.sessionId());
    put(r, "IpAddress", v.ipAddress());
    put(r, "Error", v.error());
    if (v.isAdminEvent()) {
      put(r, "AuthRealm", v.authRealm());
      put(r, "OperationType", v.nativeType());
      put(r, "ResourceType", v.resourceType());
      put(r, "ResourcePath", v.resourcePath());
      put(r, "Representation", truncate(v.representation()));
      // keycloak-events adds the affected user to admin events on users
      put(r, "TargetUserId", v.details().get("userId"));
      put(r, "TargetUsername", v.details().get("username"));
    }
    if (!v.details().isEmpty()) r.put("Details", v.details());
    return r;
  }

  private void put(Map<String, Object> r, String k, String v) {
    if (v != null) r.put(k, v);
  }

  private String truncate(String s) {
    return s == null || s.length() <= MAX_FIELD_CHARS ? s : s.substring(0, MAX_FIELD_CHARS);
  }

  /**
   * The {@code streamDeclarations} columns JSON for a data collection rule.
   *
   * @return the columns as a JSON array of {@code {"name":...,"type":...}} objects
   */
  public String streamColumnsJson() {
    return COLUMNS.stream()
        .map(c -> String.format("{\"name\":\"%s\",\"type\":\"%s\"}", c.name(), c.type()))
        .collect(Collectors.joining(",", "[", "]"));
  }
}

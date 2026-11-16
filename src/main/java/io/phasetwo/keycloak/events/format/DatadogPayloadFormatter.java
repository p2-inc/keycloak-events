package io.phasetwo.keycloak.events.format;

import com.google.auto.service.AutoService;
import com.google.common.base.Strings;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.util.JsonSerialization;
import org.keycloak.utils.StringUtil;

/**
 * Formats events for the Datadog Logs HTTP intake ({@code POST
 * https://http-intake.logs.<site>/api/v2/logs}, authenticated with a {@code DD-API-KEY} header).
 *
 * <p>Each event becomes a log entry with {@code ddsource=keycloak} whose {@code message} is the
 * line Keycloak's own {@code org.keycloak.events} logger writes ({@code type="LOGIN",
 * realmId="...", realmName="...", clientId="...", userId="...", ...}). That is exactly what
 * Datadog's Keycloak integration pipeline parses, so its attribute remappers, GeoIP enrichment and
 * the Keycloak Cloud SIEM content pack work unchanged. The entries also carry Datadog standard
 * attributes ({@code usr.*}, {@code network.client.ip}, {@code evt.*}) and the full event under
 * {@code keycloak}, so detections don't depend on the message being parsed.
 */
public class DatadogPayloadFormatter implements WebhookPayloadFormatter {

  public static final String ID = "datadog";

  /** The logger whose output Datadog's Keycloak pipeline classifies as user and admin events. */
  static final String EVENTS_LOGGER = "org.keycloak.events";

  static final String CONFIG_SOURCE = "source";
  static final String CONFIG_SERVICE = "service";
  static final String CONFIG_HOSTNAME = "hostname";
  static final String CONFIG_TAGS = "tags";

  static final String DEFAULT_SOURCE = "keycloak";
  static final String DEFAULT_SERVICE = "keycloak";

  /** Datadog accepts up to 1000 entries per request. */
  static final int MAX_BATCH = 1000;

  /** Status codes Datadog says not to retry as-is: bad payload, bad API key, payload too large. */
  static final Set<Integer> NON_RETRYABLE = Set.of(400, 401, 403, 413);

  private static final char QUOTE = '"';

  @Override
  public FormattedPayload format(List<ExtendedAdminEvent> events, Map<String, String> config)
      throws IOException {
    List<Map<String, Object>> entries = new ArrayList<>(events.size());
    for (ExtendedAdminEvent e : events) entries.add(entry(EventView.of(e), config));
    return FormattedPayload.json(JsonSerialization.writeValueAsString(entries));
  }

  @Override
  public int maxBatchSize() {
    return MAX_BATCH;
  }

  @Override
  public boolean isRetryable(int httpStatus) {
    return !NON_RETRYABLE.contains(httpStatus);
  }

  static Map<String, Object> entry(EventView v, Map<String, String> config) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ddsource", configOr(config, CONFIG_SOURCE, DEFAULT_SOURCE));
    m.put("service", configOr(config, CONFIG_SERVICE, DEFAULT_SERVICE));
    m.put("hostname", configOr(config, CONFIG_HOSTNAME, defaultHostname()));
    String tags = config.get(CONFIG_TAGS);
    if (!Strings.isNullOrEmpty(tags)) m.put("ddtags", tags);
    m.put("message", message(v));
    m.put("timestamp", v.timeMillis());
    m.put("status", v.isError() ? "warn" : "info");
    m.put("loggerName", EVENTS_LOGGER);
    m.put("logger", Map.of("name", EVENTS_LOGGER));

    Map<String, Object> evt = new LinkedHashMap<>();
    evt.put("name", v.nativeType());
    evt.put("outcome", v.isError() ? "failure" : "success");
    String category = eventCategory(v);
    if (category != null) evt.put("category", category);
    m.put("evt", evt);

    Map<String, Object> usr = new LinkedHashMap<>();
    putIfNotNull(usr, "id", v.userId());
    putIfNotNull(usr, "name", v.username());
    if (v.isUserEvent()) putIfNotNull(usr, "email", v.details().get("email"));
    if (!usr.isEmpty()) m.put("usr", usr);

    if (v.ipAddress() != null) m.put("network", Map.of("client", Map.of("ip", v.ipAddress())));
    if (v.isError()) m.put("error", Map.of("message", v.error()));

    Map<String, Object> kc = new LinkedHashMap<>();
    kc.put("id", v.id());
    putIfNotNull(kc, "uid", v.uid());
    kc.put("type", v.type());
    kc.put("category", v.category());
    putIfNotNull(kc, "realmId", v.realmId());
    putIfNotNull(kc, "realmName", v.realmName());
    putIfNotNull(kc, "clientId", v.clientId());
    putIfNotNull(kc, "sessionId", v.sessionId());
    putIfNotNull(kc, "error", v.error());
    if (v.isAdminEvent()) {
      putIfNotNull(kc, "operationType", v.nativeType());
      putIfNotNull(kc, "resourceType", v.resourceType());
      putIfNotNull(kc, "resourcePath", v.resourcePath());
      putIfNotNull(kc, "authRealm", v.authRealm());
    }
    if (!v.details().isEmpty()) kc.put("details", v.details());
    m.put("keycloak", kc);
    return m;
  }

  /**
   * The line Keycloak's {@code JBossLoggingEventListenerProvider} logs for the event, with its
   * default quoting and sanitizing. Datadog's Keycloak pipeline grok-parses this format.
   */
  static String message(EventView v) {
    StringBuilder sb = new StringBuilder();
    if (v.isAdminEvent()) {
      sb.append("operationType=");
      quoted(sb, v.nativeType());
      sb.append(", realmId=");
      quoted(sb, v.realmId());
      sb.append(", realmName=");
      quoted(sb, v.realmName());
      sb.append(", clientId=");
      quoted(sb, v.clientId());
      sb.append(", userId=");
      quoted(sb, v.userId());
      sb.append(", ipAddress=");
      quoted(sb, v.ipAddress());
      sb.append(", resourceType=");
      quoted(sb, v.resourceType());
      sb.append(", resourcePath=");
      quoted(sb, v.resourcePath());
    } else {
      sb.append("type=");
      quoted(sb, v.nativeType());
      sb.append(", realmId=");
      quoted(sb, v.realmId());
      sb.append(", realmName=");
      quoted(sb, v.realmName());
      sb.append(", clientId=");
      quoted(sb, v.clientId());
      sb.append(", userId=");
      quoted(sb, v.userId());
      if (v.sessionId() != null) {
        sb.append(", sessionId=");
        quoted(sb, v.sessionId());
      }
      sb.append(", ipAddress=");
      quoted(sb, v.ipAddress());
    }
    if (v.isError()) {
      sb.append(", error=");
      quoted(sb, v.error());
    }
    for (Map.Entry<String, String> d : v.details().entrySet()) {
      sb.append(", ");
      sb.append(StringUtil.sanitizeSpacesAndQuotes(detailKey(v, d.getKey()), null));
      sb.append("=");
      quoted(sb, d.getValue());
    }
    return sb.toString();
  }

  /** Keys of the fixed part of Keycloak's event line. */
  static final Set<String> LINE_KEYS =
      Set.of(
          "type",
          "operationType",
          "realmId",
          "realmName",
          "clientId",
          "userId",
          "sessionId",
          "ipAddress",
          "error",
          "resourceType",
          "resourcePath");

  /**
   * The key a detail is written under in the event line. keycloak-events adds the target user's
   * {@code userId}/{@code username} to admin events on users; written as-is they would collide with
   * the acting admin's {@code userId} and be remapped to {@code usr.*} by Datadog's pipeline, so
   * they become {@code resourceUserId}/{@code resourceUsername}. Any other detail that collides
   * with a fixed key gets a {@code detail_} prefix.
   */
  static String detailKey(EventView v, String key) {
    if (v.isAdminEvent()) {
      if ("userId".equals(key)) return "resourceUserId";
      if ("username".equals(key)) return "resourceUsername";
      if ("email".equals(key)) return "resourceEmail";
    }
    return LINE_KEYS.contains(key) ? "detail_" + key : key;
  }

  private static void quoted(StringBuilder sb, String value) {
    sb.append(QUOTE).append(StringUtil.sanitizeSpacesAndQuotes(value, QUOTE)).append(QUOTE);
  }

  /** Datadog's {@code evt.category} for the events Cloud SIEM's authentication rules look at. */
  static String eventCategory(EventView v) {
    if (!v.isUserEvent()) return v.isAdminEvent() ? "admin" : null;
    String t = v.nativeType();
    if (t.startsWith("LOGIN")
        || t.startsWith("LOGOUT")
        || t.startsWith("CODE_TO_TOKEN")
        || t.startsWith("CLIENT_LOGIN")
        || t.startsWith("REFRESH_TOKEN")
        || t.startsWith("IDENTITY_PROVIDER_LOGIN")
        || t.startsWith("IMPERSONATE")) {
      return "authentication";
    }
    return null;
  }

  private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
    if (v != null) m.put(k, v);
  }

  private static String configOr(Map<String, String> config, String key, String def) {
    String v = config.get(key);
    return Strings.isNullOrEmpty(v) ? def : v;
  }

  private static String defaultHostname() {
    String h = System.getenv("HOSTNAME");
    if (!Strings.isNullOrEmpty(h)) return h;
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "keycloak";
    }
  }

  @AutoService(WebhookPayloadFormatterFactory.class)
  public static class Factory implements WebhookPayloadFormatterFactory {

    @Override
    public WebhookPayloadFormatter create(KeycloakSession session) {
      return new DatadogPayloadFormatter();
    }

    @Override
    public String getId() {
      return ID;
    }

    @Override
    public String getHelpText() {
      return "Datadog Logs intake (POST https://http-intake.logs.<site>/api/v2/logs). Use authType"
          + " 'headers' with config header.DD-API-KEY. Entries use ddsource=keycloak and"
          + " Keycloak's native event log line, so Datadog's Keycloak pipeline and Cloud SIEM"
          + " content pack apply.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
      return ProviderConfigurationBuilder.create()
          .property()
          .name(CONFIG_SERVICE)
          .label("Service")
          .helpText("The Datadog service attribute.")
          .type(ProviderConfigProperty.STRING_TYPE)
          .defaultValue(DEFAULT_SERVICE)
          .add()
          .property()
          .name(CONFIG_SOURCE)
          .label("Source")
          .helpText(
              "The ddsource attribute. Keep 'keycloak' to use Datadog's Keycloak integration.")
          .type(ProviderConfigProperty.STRING_TYPE)
          .defaultValue(DEFAULT_SOURCE)
          .add()
          .property()
          .name(CONFIG_HOSTNAME)
          .label("Hostname")
          .helpText("The hostname attribute. Defaults to this Keycloak node's hostname.")
          .type(ProviderConfigProperty.STRING_TYPE)
          .add()
          .property()
          .name(CONFIG_TAGS)
          .label("Tags")
          .helpText("Comma separated ddtags, e.g. env:prod,team:iam.")
          .type(ProviderConfigProperty.STRING_TYPE)
          .add()
          .build();
    }
  }
}

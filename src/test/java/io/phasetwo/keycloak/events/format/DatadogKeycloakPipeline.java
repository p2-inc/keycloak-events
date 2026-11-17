package io.phasetwo.keycloak.events.format;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parsing rule of Datadog's Keycloak log pipeline
 * (integrations-core/keycloak/assets/logs/keycloak.yaml) rewritten as Java regexes, to check that
 * messages parse the way Datadog would parse them.
 */
public class DatadogKeycloakPipeline {

  /**
   * The match rule: {@code %{data::keyvalue(...)} realmName="%{data:realmName}",
   * clientId="%{data:clientId}", userId="%{notSpace:userId}", %{data::keyvalue(...)}}.
   */
  private final Pattern grok =
      Pattern.compile(
          "^(.*) realmName=\"(.*)\", clientId=\"(.*)\", userId=\"(\\S+)\", (.*)$", Pattern.DOTALL);

  private final Pattern keyValue = Pattern.compile("(\\w+)=\"((?:[^\"\\\\]|\\\\.)*)\"");

  /**
   * Whether the pipeline's match rule accepts a message.
   *
   * @param message a log message
   * @return true if it matches
   */
  public boolean matches(String message) {
    return grok.matcher(message).matches();
  }

  /**
   * The attributes the pipeline's keyvalue filter extracts from a message.
   *
   * @param message a log message
   * @return attribute name to value, in message order
   */
  public Map<String, String> attributes(String message) {
    Map<String, String> m = new LinkedHashMap<>();
    Matcher matcher = keyValue.matcher(message);
    while (matcher.find()) m.put(matcher.group(1), matcher.group(2));
    return m;
  }
}

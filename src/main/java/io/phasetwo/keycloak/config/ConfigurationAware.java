package io.phasetwo.keycloak.config;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public interface ConfigurationAware {

  String getId();

  default List<Map<String, Object>> getConfigurations(KeycloakSession session) {
    return RealmAttributesConfigLoader.loadConfigurations(
            session, getRealm(session).getName(), getId())
        .stream()
        .map(config -> RealmAttributesConfigLoader.safeConvertToMap(config))
        .collect(Collectors.toList());
  }

  static RealmModel getRealm(KeycloakSession session) {
    if (session.getContext() == null) {
      return null;
    }
    if (session.getContext().getRealm() != null) {
      return session.getContext().getRealm();
    }
    if (session.getContext().getAuthenticationSession() != null
        && session.getContext().getAuthenticationSession().getRealm() != null) {
      return session.getContext().getAuthenticationSession().getRealm();
    }
    if (session.getContext().getClient() != null
        && session.getContext().getClient().getRealm() != null) {
      return session.getContext().getClient().getRealm();
    }
    return null;
  }

  default Map<String, Object> getConfiguration(KeycloakSession session) {
    List<Map<String, Object>> configs = getConfigurations(session);
    if (configs == null || configs.size() == 0) return ImmutableMap.of();
    else return configs.get(0);
  }

  default String configToString(Map<String, Object> config) {
    if (config == null) return "[empty]";
    else return "[" + Joiner.on(",").withKeyValueSeparator("=").join(config) + "]";
  }
}

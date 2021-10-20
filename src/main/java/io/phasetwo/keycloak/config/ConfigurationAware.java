package io.phasetwo.keycloak.config;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.keycloak.models.KeycloakSession;

public interface ConfigurationAware {

  String getId();

  default List<Map<String, Object>> getConfigurations(KeycloakSession session) {
    return RealmAttributesConfigLoader.loadConfigurations(
            session, session.getContext().getRealm().getName(), getId())
        .stream()
        .map(config -> RealmAttributesConfigLoader.safeConvertToMap(config))
        .collect(Collectors.toList());
  }

  default Map<String, Object> getConfiguration(KeycloakSession session) {
    List<Map<String, Object>> configs = getConfigurations(session);
    if (configs == null || configs.size() == 0) return ImmutableMap.of();
    else return configs.get(0);
  }
}

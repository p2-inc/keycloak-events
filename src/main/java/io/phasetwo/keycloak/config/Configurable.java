package io.phasetwo.keycloak.config;

import java.util.Map;

public interface Configurable {
  void setConfig(Map<String, Object> config);

  default String getOr(Map<String, Object> config, String key, String defaultValue) {
    if (config == null) return defaultValue;
    Object o = config.get(key);
    if (o != null) return o.toString();
    else return defaultValue;
  }

  default boolean getBooleanOr(Map<String, Object> config, String key, boolean defaultValue) {
    if (config == null) return defaultValue;
    Object o = config.get(key);
    if (o != null) {
      if (o instanceof String) {
        return Boolean.parseBoolean((String) o);
      } else {
        return (boolean) o;
      }
    } else {
      return defaultValue;
    }
  }

  default int getIntOr(Map<String, Object> config, String key, int defaultValue) {
    if (config == null) return defaultValue;
    Object o = config.get(key);
    if (o != null) {
      if (o instanceof String) {
        return Integer.parseInt((String) o);
      } else {
        return (int) o;
      }
    } else {
      return defaultValue;
    }
  }

  default double getDoubleOr(Map<String, Object> config, String key, double defaultValue) {
    if (config == null) return defaultValue;
    Object o = config.get(key);
    if (o != null) {
      if (o instanceof String) {
        return Double.parseDouble((String) o);
      } else {
        return (double) o;
      }
    } else {
      return defaultValue;
    }
  }
}

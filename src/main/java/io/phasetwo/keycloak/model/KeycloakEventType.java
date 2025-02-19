package io.phasetwo.keycloak.model;

public enum KeycloakEventType {
  USER,
  ADMIN,
  SYSTEM,
  UNKNOWN;

  public boolean keycloakNative() {
    return (this == USER || this == ADMIN);
  }

  static KeycloakEventType from(String input) {
    try {
      return KeycloakEventType.valueOf(KeycloakEventType.class, input.toUpperCase());
    } catch (Exception e) {
      return KeycloakEventType.UNKNOWN;
    }
  }

  public static KeycloakEventType fromTypeString(String input) {
    if (input == null || input.isEmpty()) {
      return KeycloakEventType.UNKNOWN;
    }

    for (KeycloakEventType type : KeycloakEventType.values()) {
      if (input.toUpperCase().startsWith(type.name() + ".")) {
        return type;
      }
    }

    if (input.toUpperCase().startsWith("ACCESS.")) {
      return KeycloakEventType.USER;
    }

    return KeycloakEventType.UNKNOWN;
  }
}

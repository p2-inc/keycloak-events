package io.phasetwo.keycloak.events.aws;

import java.io.IOException;
import java.util.Map;
import org.keycloak.util.JsonSerialization;

public class FlatEvents {
  protected static String serializeDetails(Map<String, String> details) {
    if (details == null) {
      return null;
    }
    try {
      return JsonSerialization.writeValueAsString(details);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to serialize admin event details", e);
    }
  }
}

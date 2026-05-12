package io.phasetwo.keycloak.events;

import com.fasterxml.jackson.core.type.TypeReference;
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

  protected static Map<String, String> deserializeDetails(String detailsJson) {
    if (detailsJson == null || detailsJson.trim().isEmpty()) {
      return null;
    }
    try {
      return JsonSerialization.readValue(detailsJson, new TypeReference<Map<String, String>>() {});
    } catch (IOException e) {
      throw new IllegalStateException("Unable to deserialize event details", e);
    }
  }
}

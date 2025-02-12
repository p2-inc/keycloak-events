package io.phasetwo.keycloak.representation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Credential {
  @JsonProperty("type")
  private String type;

  @JsonProperty("value")
  private String value;
}

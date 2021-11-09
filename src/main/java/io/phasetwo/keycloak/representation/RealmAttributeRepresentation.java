package io.phasetwo.keycloak.representation;

import lombok.Data;

@Data
public class RealmAttributeRepresentation {
  private String name;
  private String value;
  private String realm;
}

package io.phasetwo.keycloak.representation;

import java.util.Date;
import java.util.Set;
import lombok.Data;

@Data
public class WebhookRepresentation {
  private String id;
  private boolean enabled;
  private String url;
  private String secret;
  private String createdBy;
  private Date createdAt;
  private String realm;
  private Set<String> eventTypes;
}

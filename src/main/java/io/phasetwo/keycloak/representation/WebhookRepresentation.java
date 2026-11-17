package io.phasetwo.keycloak.representation;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class WebhookRepresentation {
  private String id;
  private boolean enabled;
  private String url;
  private String secret;
  private String algorithm;
  private String authType;
  private String audience;
  private String payloadFormat;
  private Map<String, String> config;
  private String createdBy;
  private Date createdAt;
  private String realm;
  private Set<String> eventTypes;
}

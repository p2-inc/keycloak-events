package io.phasetwo.keycloak.representation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import lombok.Data;

@Data
public class WebhookSend {
  private String id;

  @JsonProperty("type")
  private String eventType;

  private Integer status;

  @JsonProperty("status_message")
  private String statusMessage;

  private Integer retries;

  @JsonProperty("sent_at")
  private Date sentAt;

  @JsonProperty("event_id")
  private String eventId;

  @JsonProperty("keycloak_event_type")
  private String keycloakEventType;

  @JsonProperty("keycloak_event_id")
  private String keycloakEventId;

  private String payload;
}

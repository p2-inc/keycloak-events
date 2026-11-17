package io.phasetwo.keycloak.representation;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.keycloak.representations.idm.ConfigPropertyRepresentation;

/** An installed webhook payload format and the config options it reads. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayloadFormatRepresentation {
  private String id;
  private String helpText;
  private List<ConfigPropertyRepresentation> properties;
}

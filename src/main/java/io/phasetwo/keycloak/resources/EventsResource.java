package io.phasetwo.keycloak.resources;

import io.phasetwo.keycloak.events.WebhookSenderEventListenerProvider;
import io.phasetwo.keycloak.events.WebhookSenderEventListenerProviderFactory;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.models.KeycloakSession;

/** */
@JBossLog
public class EventsResource extends AbstractAdminResource {

  public EventsResource(KeycloakSession session) {
    super(session);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response publishEvent(@Valid ExtendedAdminEvent body) {
    log.infof("Publish event for %s %s", realm.getName(), body);

    requireAdminRole(EventsResourceProviderFactory.ROLE_PUBLISH_EVENTS);

    // validation
    if (body.getType() == null) {
      throw new BadRequestException("Event must contain a type");
    } else if (body.getType().toLowerCase().startsWith("access.")
        || body.getType().toLowerCase().startsWith("admin.")
        || body.getType().toLowerCase().startsWith("system.")) {
      throw new ClientErrorException("Reserved event type.", 409);
    }

    // set time if not set TODO this should be a better check. fucking primitives
    if (body.getTime() < 1) body.setTime(System.currentTimeMillis());

    // hydrate authdetails
    body.setAuthDetails(getAuthDetails());

    getEventEmitter().ifPresent(e -> e.processEvent(body, realm.getId()));

    //    return Response.accepted().build();
    //    return Response.noContent().status(202).build();//hack jax-rs doesn't like accepted()
    // without content-type set
    return Response.accepted().type(MediaType.WILDCARD).build();
  }

  private ExtendedAuthDetails getAuthDetails() {
    ExtendedAuthDetails details = new ExtendedAuthDetails(null);
    details.setRealmId(auth.getRealm().getName());
    details.setClientId(auth.getClient().getClientId());
    details.setUserId(auth.getUser().getId());
    details.setUsername(auth.getUser().getUsername());
    optionalOf(() -> session.getContext().getConnection().getRemoteAddr())
        .ifPresent(details::setIpAddress);
    optionalOf(() -> session.getContext().getAuthenticationSession().getParentSession().getId())
        .ifPresent(details::setSessionId);
    return details;
  }

  static <T> Optional<T> optionalOf(Supplier<T> supplier) {
    try {
      return Optional.ofNullable(supplier.get());
    } catch (Exception e) {
    }
    return Optional.empty();
  }

  private Optional<WebhookSenderEventListenerProvider> getEventEmitter() {
    EventListenerProvider listener =
        session.getProvider(
            EventListenerProvider.class, WebhookSenderEventListenerProviderFactory.PROVIDER_ID);
    if (listener != null && listener instanceof WebhookSenderEventListenerProvider) {
      return Optional.of((WebhookSenderEventListenerProvider) listener);
    } else {
      return Optional.empty();
    }
  }
}

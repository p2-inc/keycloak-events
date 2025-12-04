package io.phasetwo.keycloak.resources;

import io.phasetwo.keycloak.events.WebhookSenderEventListenerProvider;
import io.phasetwo.keycloak.model.KeycloakEventType;
import io.phasetwo.keycloak.model.WebhookEventModel;
import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.model.WebhookSendModel;
import io.phasetwo.keycloak.representation.Credential;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import io.phasetwo.keycloak.representation.WebhookSend;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.resources.admin.AdminRoot;

@JBossLog
public class WebhooksResource extends AbstractAdminResource {

  private final WebhookProvider webhooks;

  public WebhooksResource(KeycloakSession session) {
    super(session);
    this.webhooks = session.getProvider(WebhookProvider.class);
  }

  private static final Integer DEFAULT_MAX_RESULTS = 100;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<WebhookRepresentation> getWebhooks(
      @QueryParam("first") Integer firstResult, @QueryParam("max") Integer maxResults) {
    permissions.realm().requireViewEvents();
    firstResult = firstResult != null ? firstResult : 0;
    maxResults =
        (maxResults != null && maxResults <= DEFAULT_MAX_RESULTS)
            ? maxResults
            : DEFAULT_MAX_RESULTS;
    return webhooks.getWebhooksStream(realm, firstResult, maxResults).map(w -> toRepresentation(w));
  }

  @GET
  @Path("count")
  @Produces(MediaType.APPLICATION_JSON)
  public Long countWebhooks(@QueryParam("search") String searchQuery) {
    log.debugf("countWebhooks %s %s", realm.getName(), searchQuery);
    permissions.realm().requireViewEvents();
    return webhooks.getWebhooksCount(realm);
  }

  private WebhookRepresentation toRepresentation(WebhookModel w) {
    WebhookRepresentation webhook = new WebhookRepresentation();
    webhook.setId(w.getId());
    webhook.setEnabled(w.isEnabled());
    webhook.setUrl(w.getUrl());
    UserModel u = w.getCreatedBy();
    if (u != null) {
      webhook.setCreatedBy(u.getId());
    }
    webhook.setCreatedAt(w.getCreatedAt());
    webhook.setRealm(w.getRealm().getName());
    webhook.setEventTypes(w.getEventTypes());
    // no secret
    return webhook;
  }

  private WebhookSend toRepresentation(WebhookSendModel s, boolean brief) {
    WebhookSend send = new WebhookSend();
    send.setId(s.getId());
    send.setEventId(s.getEvent().getId()); //
    send.setEventType(s.getEventType());
    send.setStatus(s.getStatus());
    send.setStatusMessage(getStatusMessage(s.getStatus()));
    send.setRetries(s.getRetries());
    send.setSentAt(s.getSentAt());
    KeycloakEventType kcType = s.getEvent().getEventType();
    send.setKeycloakEventType(kcType.name());
    if (kcType == KeycloakEventType.USER) {
      send.setKeycloakEventId(s.getEvent().getEventId());
    }
    if (kcType == KeycloakEventType.ADMIN) {
      send.setKeycloakEventId(s.getEvent().getAdminEventId());
    }
    if (!brief) send.setPayload(s.getEvent().rawPayload());
    WebhookModel w = s.getWebhook();
    // brief representation for the send
    WebhookRepresentation webhook = new WebhookRepresentation();
    webhook.setId(w.getId());
    webhook.setEnabled(w.isEnabled());
    webhook.setUrl(w.getUrl());
    send.setWebhook(webhook);
    return send;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createWebhook(final WebhookRepresentation rep) {
    permissions.realm().requireManageEvents();
    validateWebhook(rep);
    WebhookModel w = webhooks.createWebhook(realm, rep.getUrl(), auth.getUser());
    mergeWebhook(rep, w);
    // /auth/realms/:realm/webhooks/:id
    URI location =
        AdminRoot.realmsUrl(session.getContext().getUri())
            .path(realm.getName())
            .path("webhooks")
            .path(w.getId())
            .build();
    WebhookRepresentation webhookRepresentation = toRepresentation(w);
    return Response.status(Response.Status.CREATED).entity(webhookRepresentation).location(location).build();
  }

  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public WebhookRepresentation getWebhook(final @PathParam("id") String id) {
    permissions.realm().requireViewEvents();
    WebhookModel w = webhooks.getWebhookById(realm, id);
    if (w != null) return toRepresentation(w);
    else throw new NotFoundException(String.format("no webhook with id %s", id));
  }

  @GET
  @Path("{id}/secret")
  @Produces(MediaType.APPLICATION_JSON)
  public Credential getWebhookSecret(final @PathParam("id") String id) {
    permissions.realm().requireManageEvents();
    WebhookModel w = webhooks.getWebhookById(realm, id);
    if (w != null) {
      Credential c = new Credential();
      c.setType("secret");
      c.setValue(w.getSecret());
      return c;
    } else throw new NotFoundException(String.format("no webhook with id %s", id));
  }

  @GET
  @Path("{id}/sends")
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<WebhookSend> getWebhookSends(
      final @PathParam("id") String id,
      @QueryParam("first") Integer firstResult,
      @QueryParam("max") Integer maxResults) {
    permissions.realm().requireViewEvents();
    firstResult = firstResult != null ? firstResult : 0;
    maxResults =
        (maxResults != null && maxResults <= DEFAULT_MAX_RESULTS)
            ? maxResults
            : DEFAULT_MAX_RESULTS;
    WebhookModel w = webhooks.getWebhookById(realm, id);
    if (w == null) {
      throw new NotFoundException(String.format("no webhook with id %s", id));
    }
    return webhooks.getSends(realm, w, firstResult, maxResults).map(s -> toRepresentation(s, true));
  }

  @GET
  @Path("payload/{type}/{kid}")
  @Produces(MediaType.APPLICATION_JSON)
  public ExtendedAdminEvent getPayload(
      final @PathParam("type") String type, final @PathParam("kid") String kid) throws Exception {
    permissions.realm().requireViewEvents();

    if (type == null || !("admin".equals(type) || "user".equals(type))) {
      throw new BadRequestException(String.format("type %s not allowed", type));
    }

    WebhookEventModel event =
        webhooks.getEvent(
            realm, "admin".equals(type) ? KeycloakEventType.ADMIN : KeycloakEventType.USER, kid);

    if (event == null) {
      throw new NotFoundException(String.format("%s %s not found", type, kid));
    }

    return event.getPayload(ExtendedAdminEvent.class);
  }

  @GET
  @Path("sends/{type}/{kid}")
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<WebhookSend> getWebhookSendsForEvent(
      final @PathParam("type") String type, final @PathParam("kid") String kid) throws Exception {
    permissions.realm().requireViewEvents();

    if (type == null || !("admin".equals(type) || "user".equals(type))) {
      throw new BadRequestException(String.format("type %s not allowed", type));
    }

    return webhooks
        .getSends(
            realm, "admin".equals(type) ? KeycloakEventType.ADMIN : KeycloakEventType.USER, kid)
        .map(s -> toRepresentation(s, true));
  }

  @GET
  @Path("{id}/sends/{sid}")
  @Produces(MediaType.APPLICATION_JSON)
  public WebhookSend getWebhookSend(
      final @PathParam("id") String id, final @PathParam("sid") String sid) {
    permissions.realm().requireViewEvents();
    WebhookModel w = webhooks.getWebhookById(realm, id);
    if (w == null) {
      throw new NotFoundException(String.format("no webhook with id %s", id));
    }
    WebhookSendModel s = webhooks.getSendById(realm, sid);
    if (s == null) {
      throw new NotFoundException(String.format("no webhook send with id %s", sid));
    }
    if (!w.getId().equals(s.getWebhook().getId())) {
      throw new NotFoundException(String.format("no webhook %s and send %s", id, sid));
    }
    return toRepresentation(s, false);
  }

  private static final String UNKNOWN = "Unknown Status Code";
  private static final String STATUS_MESSAGE_FORMAT = "HTTP %d %s";

  static String getStatusMessage(Integer statusCode) {
    if (statusCode == null) return String.format(STATUS_MESSAGE_FORMAT, 999, UNKNOWN);

    Response.Status status = Response.Status.fromStatusCode(statusCode);
    String statusStr = (status != null) ? status.getReasonPhrase().replace("_", " ") : UNKNOWN;
    return String.format(STATUS_MESSAGE_FORMAT, statusCode, statusStr);
  }

  @POST
  @Path("{id}/sends/{sid}/resend")
  public Response resend(final @PathParam("id") String id, final @PathParam("sid") String sid)
      throws Exception {
    permissions.realm().requireManageEvents();
    WebhookModel w = webhooks.getWebhookById(realm, id);
    if (w == null) {
      throw new NotFoundException(String.format("no webhook with id %s", id));
    }
    WebhookSendModel s = webhooks.getSendById(realm, sid);
    if (s == null) {
      throw new NotFoundException(String.format("no webhook send with id %s", sid));
    }
    if (!w.getId().equals(s.getWebhook().getId())) {
      throw new NotFoundException(String.format("no webhook %s and send %s", id, sid));
    }

    WebhookSenderEventListenerProvider listener =
        (WebhookSenderEventListenerProvider)
            session.getProvider(EventListenerProvider.class, "ext-event-webhook");
    if (listener == null) {
      log.warn("couldn't find ext-event-webhook provider");
    } else {
      ExtendedAdminEvent customEvent = s.getEvent().getPayload(ExtendedAdminEvent.class);
      customEvent.setUid(sid);
      listener.schedule(w, customEvent);
    }

    return Response.accepted().type(MediaType.TEXT_PLAIN).build();
  }

  @PUT
  @Path("{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateWebhook(final @PathParam("id") String id, WebhookRepresentation rep) {
    permissions.realm().requireManageEvents();
    validateWebhook(rep);
    WebhookModel w = webhooks.getWebhookById(realm, id);
    if (w == null) throw new NotFoundException(String.format("no webhook with id %s", id));
    mergeWebhook(rep, w);
    return Response.noContent().build();
  }

  private void validateWebhook(WebhookRepresentation rep) {
    if (rep == null) throw new BadRequestException("webhook cannot be empty");
    if (rep.getUrl() == null) throw new BadRequestException("url cannot be empty");
    try {
      new URI(rep.getUrl()).parseServerAuthority();
    } catch (URISyntaxException e) {
      throw new BadRequestException(rep.getUrl() + " not a URL");
    }
  }

  private void mergeWebhook(WebhookRepresentation rep, WebhookModel w) {
    w.setUrl(rep.getUrl());
    w.setEnabled(rep.isEnabled());
    if (rep.getEventTypes() != null) {
      w.removeEventTypes();
      rep.getEventTypes().forEach(t -> w.addEventType(t));
    }
    if (rep.getSecret() != null && !"".equals(rep.getSecret())) {
      w.setSecret(rep.getSecret());
    }
    if (rep.getAlgorithm() != null && !"".equals(rep.getAlgorithm())) {
      w.setAlgorithm(rep.getAlgorithm());
    } else {
      w.setAlgorithm("HmacSHA256");
    }
  }

  @DELETE
  @Path("{id}")
  public Response removeWebhook(final @PathParam("id") String id) {
    permissions.realm().requireManageEvents();
    getWebhook(id); // forces a not found if it doesn't exist
    webhooks.removeWebhook(realm, id);
    return Response.noContent().build();
  }
}

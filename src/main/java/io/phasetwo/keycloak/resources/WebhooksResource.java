package io.phasetwo.keycloak.resources;

import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import javax.validation.constraints.*;
import javax.ws.rs.*;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.AdminRoot;

@JBossLog
public class WebhooksResource extends AbstractAdminResource {

  private final WebhookProvider webhooks;

  public WebhooksResource(RealmModel realm, WebhookProvider webhooks) {
    super(realm);
    this.webhooks = webhooks;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<WebhookRepresentation> getWebhooks() {
    permissions.realm().requireViewEvents();
    return webhooks.getWebhooksStream(realm).map(w -> toRepresentation(w));
  }

  private WebhookRepresentation toRepresentation(WebhookModel w) {
    WebhookRepresentation webhook = new WebhookRepresentation();
    webhook.setId(w.getId());
    webhook.setEnabled(w.isEnabled());
    webhook.setUrl(w.getUrl());
    webhook.setCreatedBy(w.getCreatedBy().getId());
    webhook.setCreatedAt(w.getCreatedAt());
    webhook.setRealm(w.getRealm().getName());
    webhook.setEventTypes(w.getEventTypes());
    // no secret
    return webhook;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
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
    return Response.created(location).build();
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

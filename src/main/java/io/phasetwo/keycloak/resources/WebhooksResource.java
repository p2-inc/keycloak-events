package io.phasetwo.keycloak.resources;

import static io.phasetwo.keycloak.webhooks.Webhooks.*;

import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
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
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resources.admin.AdminRoot;

@JBossLog
public class WebhooksResource extends AbstractAdminResource {

  private final WebhookProvider webhooks;

  public WebhooksResource(KeycloakSession session) {
    super(session);
    this.webhooks = session.getProvider(WebhookProvider.class);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<WebhookRepresentation> getWebhooks() {
    permissions.realm().requireViewEvents();
    return webhooks.getWebhooksStream(realm).map(w -> toRepresentation(w));
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createWebhook(final WebhookRepresentation rep) {
    permissions.realm().requireManageEvents();
    validateWebhook(rep);
    // create the WebhookModel
    WebhookModel w = webhooks.createWebhook(realm, rep.getUrl(), auth.getUser());
    mergeWebhook(rep, w);
    // duplicate to the ComponentModel
    ComponentModel c = createComponentForWebhook(session, realm, w);
    c = realm.addComponentModel(c);
    log.debugf("Added ComponentModel with id %s for webhook %s", c.getId(), w);
    w.setComponentId(c.getId());
    // return with Location header /auth/realms/:realm/webhooks/:id
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
    // update the WebhookModel
    WebhookModel w = webhooks.getWebhookById(realm, id);
    if (w == null) throw new NotFoundException(String.format("no webhook with id %s", id));
    mergeWebhook(rep, w);
    // duplicate to the ComponentModel
    ComponentModel c = realm.getComponent(w.getComponentId());
    mergeComponent(c, w);
    realm.updateComponent(c);
    return Response.noContent().build();
  }

  @DELETE
  @Path("{id}")
  public Response removeWebhook(final @PathParam("id") String id) {
    permissions.realm().requireManageEvents();
    WebhookModel w = webhooks.getWebhookById(realm, id);
    if (w == null) throw new NotFoundException(String.format("no webhook with id %s", id));
    // delete the ComponentModel
    ComponentModel c = realm.getComponent(w.getComponentId());
    realm.removeComponent(c);
    // delete the WebhookModel
    webhooks.removeWebhook(realm, id);
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
}

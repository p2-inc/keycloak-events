package io.phasetwo.keycloak.resources;

import io.phasetwo.keycloak.ext.resource.AbstractAdminResource;
import io.phasetwo.keycloak.representation.RealmAttributeRepresentation;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;

@JBossLog
public class RealmAttributesResource extends AbstractAdminResource {

  public RealmAttributesResource(KeycloakSession session) {
    super(session);
  }

  //    this.adminEvent = adminEvent.resource(ResourceType.REALM);

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, RealmAttributeRepresentation> getAttributes() {
    if (!permissions.realm().canViewRealm())
      throw new ForbiddenException("get attributes requires view-realm");
    return realm.getAttributes().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                  RealmAttributeRepresentation r = new RealmAttributeRepresentation();
                  r.setRealm(realm.getName());
                  r.setName(e.getKey());
                  r.setValue(e.getValue());
                  return r;
                }));
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createAttribute(final RealmAttributeRepresentation rep) {
    if (!permissions.realm().canManageRealm())
      throw new ForbiddenException("create attribute requires manage-realm");
    validateRealmAttributeRepresentation(rep);
    realm.setAttribute(rep.getName(), rep.getValue());
    return Response.created(
            session.getContext().getUri().getAbsolutePathBuilder().path(rep.getName()).build())
        .build();
  }

  @GET
  @Path("{key}")
  @Produces(MediaType.APPLICATION_JSON)
  public RealmAttributeRepresentation getAttribute(final @PathParam("key") String key) {
    if (!permissions.realm().canViewRealm())
      throw new ForbiddenException("get attribute requires view-realm");
    String a = realm.getAttribute(key);
    if (a == null) throw new NotFoundException(String.format("attribute %s not present", key));
    RealmAttributeRepresentation r = new RealmAttributeRepresentation();
    r.setRealm(realm.getName());
    r.setName(key);
    r.setValue(a);
    return r;
  }

  @PUT
  @Path("{key}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateAttribute(
      final @PathParam("key") String key, RealmAttributeRepresentation rep) {
    if (!permissions.realm().canManageRealm())
      throw new ForbiddenException("update attribute requires manage-realm");
    validateRealmAttributeRepresentation(rep);
    if (!rep.getName().equals(key)) {
      throw new BadRequestException("keys must match");
    }
    realm.setAttribute(rep.getName(), rep.getValue());
    return Response.noContent().build();
  }

  @DELETE
  @Path("{key}")
  public Response removeAttribute(final @PathParam("key") String key) {
    if (!permissions.realm().canManageRealm())
      throw new ForbiddenException("remove attribute requires manage-realm");
    realm.removeAttribute(key);
    return Response.noContent().build();
  }

  protected void validateRealmAttributeRepresentation(RealmAttributeRepresentation rep) {
    if (rep.getRealm() == null || rep.getName() == null || rep.getValue() == null) {
      throw new BadRequestException("realm, name and value must be valid");
    }
    if (!rep.getRealm().equals(realm.getName())) {
      throw new BadRequestException("realm must match");
    }
    if (rep.getName().length() > 255) {
      throw new BadRequestException("key must be < 255 chars");
    }
  }
}

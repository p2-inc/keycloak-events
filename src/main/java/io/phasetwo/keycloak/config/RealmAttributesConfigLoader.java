package io.phasetwo.keycloak.config;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.jpa.entities.RealmAttributeEntity;
import org.keycloak.util.JsonSerialization;

@JBossLog
public class RealmAttributesConfigLoader {

  public static final String REALM_ATTRIBUTE_CONFIG_PREFIX = "_providerConfig";

  public static <T> Optional<T> loadConfiguration(
      KeycloakSession session, String realm, String providerId, Class<T> clazz) {
    return loadConfiguration(session, realm, providerId).map(s -> safeConvert(s, clazz));
  }

  public static Optional<String> loadConfiguration(
      KeycloakSession session, String realm, String providerId) {
    RealmModel r = session.realms().getRealmByName(realm);
    if (r == null) return Optional.empty();
    return Optional.of(r.getAttribute(getKey(providerId)));
  }

  public static <T> List<T> loadConfigurations(
      KeycloakSession session, String realm, String providerId, Class<T> clazz) {
    return loadConfigurations(session, realm, providerId).stream()
        .map(s -> safeConvert(s, clazz))
        .collect(Collectors.toList());
  }

  public static List<String> loadConfigurations(
      KeycloakSession session, String realm, String providerId) {
    log.debugf(
        "loading configurations for realm=%s, provider=%s. using query %s",
        realm, providerId, getKey(providerId));
    EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
    TypedQuery<RealmAttributeEntity> query =
        em.createQuery(
            "SELECT ra FROM RealmAttributeEntity ra WHERE ra.name LIKE :name ORDER BY ra.name",
            RealmAttributeEntity.class);
    query.setParameter("name", "" + getKey(providerId) + "%");
    return query.getResultStream().map(RealmAttributeEntity::getValue).collect(Collectors.toList());
  }

  private static String getKey(String providerId) {
    return String.format("%s.%s", REALM_ATTRIBUTE_CONFIG_PREFIX, providerId);
  }

  private static <T> T safeConvert(String s, Class<T> clazz) {
    try {
      return JsonSerialization.readValue(s, clazz);
    } catch (Exception e) {
      log.warn(e);
      return null;
    }
  }

  public static Map<String, Object> safeConvertToMap(String s) {
    try {
      return JsonSerialization.readValue(s, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.warn(e);
      return null;
    }
  }
}

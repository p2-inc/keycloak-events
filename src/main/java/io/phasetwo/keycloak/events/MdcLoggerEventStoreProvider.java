package io.phasetwo.keycloak.events;

import java.util.Date;
import java.util.stream.Stream;
import org.keycloak.events.Event;
import org.keycloak.events.EventQuery;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AdminEventQuery;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.RealmModel;

/**
 * {@link EventStoreProvider} that delegates event handling to {@link
 * MdcLoggerEventListenerProvider} and treats query/clear as no-ops. Use this when you want
 * Keycloak's events store contract satisfied by structured log output rather than by a database.
 *
 * <p>The query methods return empty {@link EventQuery}/{@link AdminEventQuery} stubs so that
 * Keycloak's Admin UI events tabs and REST endpoints respond with an empty result set instead of
 * 500ing on a null query.
 */
public class MdcLoggerEventStoreProvider extends MdcLoggerEventListenerProvider
    implements EventStoreProvider {

  @Override
  public EventQuery createQuery() {
    return new EmptyEventQuery();
  }

  @Override
  public AdminEventQuery createAdminQuery() {
    return new EmptyAdminEventQuery();
  }

  @Override
  public void clear() {}

  @Override
  public void clear(RealmModel realm) {}

  @Override
  public void clear(RealmModel realm, long olderThan) {}

  @Override
  public void clearExpiredEvents() {}

  @Override
  public void clearAdmin() {}

  @Override
  public void clearAdmin(RealmModel realm) {}

  @Override
  public void clearAdmin(RealmModel realm, long olderThan) {}

  private static final class EmptyEventQuery implements EventQuery {
    @Override
    public EventQuery type(EventType... types) {
      return this;
    }

    @Override
    public EventQuery realm(String realmId) {
      return this;
    }

    @Override
    public EventQuery client(String clientId) {
      return this;
    }

    @Override
    public EventQuery user(String userId) {
      return this;
    }

    @Override
    public EventQuery fromDate(Date fromDate) {
      return this;
    }

    @Override
    public EventQuery fromDate(long fromDate) {
      return this;
    }

    @Override
    public EventQuery toDate(Date toDate) {
      return this;
    }

    @Override
    public EventQuery toDate(long toDate) {
      return this;
    }

    @Override
    public EventQuery ipAddress(String ipAddress) {
      return this;
    }

    @Override
    public EventQuery firstResult(int firstResult) {
      return this;
    }

    @Override
    public EventQuery maxResults(int maxResults) {
      return this;
    }

    @Override
    public EventQuery orderByDescTime() {
      return this;
    }

    @Override
    public EventQuery orderByAscTime() {
      return this;
    }

    @Override
    public Stream<Event> getResultStream() {
      return Stream.empty();
    }
  }

  private static final class EmptyAdminEventQuery implements AdminEventQuery {
    @Override
    public AdminEventQuery realm(String realmId) {
      return this;
    }

    @Override
    public AdminEventQuery authRealm(String realmId) {
      return this;
    }

    @Override
    public AdminEventQuery authClient(String clientId) {
      return this;
    }

    @Override
    public AdminEventQuery authUser(String userId) {
      return this;
    }

    @Override
    public AdminEventQuery authIpAddress(String ipAddress) {
      return this;
    }

    @Override
    public AdminEventQuery operation(OperationType... operations) {
      return this;
    }

    @Override
    public AdminEventQuery resourceType(ResourceType... resourceTypes) {
      return this;
    }

    @Override
    public AdminEventQuery resourcePath(String resourcePath) {
      return this;
    }

    @Override
    public AdminEventQuery fromTime(Date fromTime) {
      return this;
    }

    @Override
    public AdminEventQuery fromTime(long fromTime) {
      return this;
    }

    @Override
    public AdminEventQuery toTime(Date toTime) {
      return this;
    }

    @Override
    public AdminEventQuery toTime(long toTime) {
      return this;
    }

    @Override
    public AdminEventQuery firstResult(int firstResult) {
      return this;
    }

    @Override
    public AdminEventQuery maxResults(int maxResults) {
      return this;
    }

    @Override
    public AdminEventQuery orderByDescTime() {
      return this;
    }

    @Override
    public AdminEventQuery orderByAscTime() {
      return this;
    }

    @Override
    public Stream<AdminEvent> getResultStream() {
      return Stream.empty();
    }
  }
}

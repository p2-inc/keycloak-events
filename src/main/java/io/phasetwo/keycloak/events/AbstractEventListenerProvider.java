package io.phasetwo.keycloak.events;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

/** */
@JBossLog
public abstract class AbstractEventListenerProvider implements EventListenerProvider {

  @Override
  public void onEvent(Event event) {
    log.debugf("%s", Events.toString(event));
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean b) {
    log.debugf("%s", Events.toString(adminEvent));
  }

  @Override
  public void close() {}
}

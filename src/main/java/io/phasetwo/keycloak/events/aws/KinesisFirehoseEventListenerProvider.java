package io.phasetwo.keycloak.events.aws;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerTransaction;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.util.JsonSerialization;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordRequest;
import software.amazon.awssdk.services.firehose.model.Record;

@JBossLog
public class KinesisFirehoseEventListenerProvider implements EventListenerProvider {

  private static final String LOGIN_EVENT_STREAM_NAME = "login-events";
  private static final String ADMIN_EVENT_STREAM_NAME = "admin-events";

  private final EventListenerTransaction tx;
  private final FirehoseClient firehose;

  public KinesisFirehoseEventListenerProvider(KeycloakSession session, FirehoseClient firehose) {
    this.tx = new EventListenerTransaction(this::logAdminEvent, this::logEvent);
    this.firehose = firehose;
  }

  @Override
  public void onEvent(Event event) {
    tx.addEvent(event);
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
    tx.addAdminEvent(adminEvent, includeRepresentation);
  }

  protected void logEvent(Event event) {
    try {
      send(JsonSerialization.writeValueAsString(event), LOGIN_EVENT_STREAM_NAME);
    } catch (Exception e) {
      log.warn("Error serializing event", e);
    }
  }

  protected void logAdminEvent(AdminEvent adminEvent, boolean realmIncludeRepresentation) {
    try {
      send(JsonSerialization.writeValueAsString(adminEvent), ADMIN_EVENT_STREAM_NAME);
    } catch (Exception e) {
      log.warn("Error serializing event", e);
    }
  }

  protected void send(String json, String stream) {
    try {
      Record record = Record.builder().data(SdkBytes.fromUtf8String(json + "\n")).build();

      firehose.putRecord(
          PutRecordRequest.builder().deliveryStreamName(stream).record(record).build());
    } catch (Exception e) {
      log.warn("Error sending to firehose", e);
    }
  }

  @Override
  public void close() {}
}

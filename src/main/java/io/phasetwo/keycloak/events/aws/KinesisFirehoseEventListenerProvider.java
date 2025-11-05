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

  private final EventListenerTransaction tx;
  private final FirehoseClient firehose;
  private final String firehoseUserEventsStream;
  private final String firehoseAdminEventsStream;

  public KinesisFirehoseEventListenerProvider(
      KeycloakSession session,
      FirehoseClient firehose,
      String firehoseUserEventsStream,
      String firehoseAdminEventsStream) {
    this.tx = new EventListenerTransaction(this::logAdminEvent, this::logEvent);
    this.firehose = firehose;
    this.firehoseUserEventsStream = firehoseUserEventsStream;
    this.firehoseAdminEventsStream = firehoseAdminEventsStream;
    session.getTransactionManager().enlistAfterCompletion(tx);
  }

  @Override
  public void onEvent(Event event) {
    log.infof("onEvent %s", event.getId());
    tx.addEvent(event);
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
    log.infof("onAdminEvent %s", adminEvent.getId());
    tx.addAdminEvent(adminEvent, includeRepresentation);
  }

  protected void logEvent(Event event) {
    try {
      send(JsonSerialization.writeValueAsString(new FlatEvent(event)), firehoseUserEventsStream);
    } catch (Exception e) {
      log.warn("Error serializing event", e);
    }
  }

  protected void logAdminEvent(AdminEvent adminEvent, boolean realmIncludeRepresentation) {
    try {
      send(
          JsonSerialization.writeValueAsString(new FlatAdminEvent(adminEvent)),
          firehoseAdminEventsStream);
    } catch (Exception e) {
      log.warn("Error serializing event", e);
    }
  }

  protected void send(String json, String stream) {
    try {
      log.infof("put record to %s:\n%s", stream, json);
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

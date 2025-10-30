package io.phasetwo.keycloak.events.aws;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.events.AbstractEventListenerProviderFactory;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import software.amazon.awssdk.services.firehose.FirehoseClient;

@JBossLog
@AutoService(EventListenerProviderFactory.class)
public class KinesisFirehoseEventListenerProviderFactory
    extends AbstractEventListenerProviderFactory {

  public static final String PROVIDER_ID = "ext-event-aws-kinesis-firehose";

  private FirehoseClient firehose;

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public KinesisFirehoseEventListenerProvider create(KeycloakSession session) {
    return new KinesisFirehoseEventListenerProvider(session, firehose);
  }

  @Override
  public void init(Config.Scope scope) {
    this.firehose = FirehoseClient.create();
  }

  @Override
  public void close() {}
}

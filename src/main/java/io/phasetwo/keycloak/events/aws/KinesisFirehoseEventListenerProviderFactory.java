package io.phasetwo.keycloak.events.aws;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.events.AbstractEventListenerProviderFactory;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseClient;

@JBossLog
@AutoService(EventListenerProviderFactory.class)
public class KinesisFirehoseEventListenerProviderFactory
    extends AbstractEventListenerProviderFactory implements EnvironmentDependentProviderFactory {

  public static final String PROVIDER_ID = "ext-event-aws-firehose";

  private FirehoseClient firehose;
  private String firehoseUserEventsStream;
  private String firehoseAdminEventsStream;

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public KinesisFirehoseEventListenerProvider create(KeycloakSession session) {
    return new KinesisFirehoseEventListenerProvider(
        session, firehose, firehoseUserEventsStream, firehoseAdminEventsStream);
  }

  @Override
  public void init(Config.Scope scope) {
    this.firehoseUserEventsStream =
        scope.get("firehoseUserEventsStream", "keycloak-events-user-events");
    this.firehoseAdminEventsStream =
        scope.get("firehoseAdminEventsStream", "keycloak-events-admin-events");
    String profile = scope.get("awsProfile");
    String region = scope.get("awsRegion");
    log.infof(
        "Config variables: awsProfile=%s, awsRegion=%s, firehoseUserEventsStream=%s, firehoseAdminEventsStream=%s",
        profile, firehoseUserEventsStream, firehoseAdminEventsStream);
    if (profile != null) {
      this.firehose =
          FirehoseClient.builder()
              .credentialsProvider(ProfileCredentialsProvider.create(profile))
              .region(Region.of(region))
              .build();
    } else {
      this.firehose = FirehoseClient.create();
    }
  }

  @Override
  public boolean isSupported(Config.Scope scope) {
    boolean enabled = scope.getBoolean("firehoseEnabled", false);
    log.infof("firehose enabled %s %b", scope.get("firehoseEnabled"), enabled);
    return enabled;
  }

  @Override
  public void close() {}
}

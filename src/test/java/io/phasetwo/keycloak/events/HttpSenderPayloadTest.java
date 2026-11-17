package io.phasetwo.keycloak.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.xgp.util.BackOff;
import io.phasetwo.keycloak.Helpers;
import io.phasetwo.keycloak.RecordingServer;
import io.phasetwo.keycloak.RecordingServer.Captured;
import io.phasetwo.keycloak.RecordingServer.Reply;
import io.phasetwo.keycloak.events.format.FormattedPayload;
import io.phasetwo.keycloak.events.format.WebhookPayloadFormatter;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.util.JsonSerialization;

/** The formatted payload is what gets sent and signed, and formatters control retries. */
public class HttpSenderPayloadTest {

  static final Duration WAIT = Duration.ofSeconds(5);

  ScheduledExecutorService exec;
  RecordingServer server;
  int port;
  AtomicInteger status = new AtomicInteger(200);

  @BeforeEach
  void setUp() throws Exception {
    exec = Executors.newSingleThreadScheduledExecutor();
    port = Helpers.nextFreePort(18100, 18200);
    server = new RecordingServer(port).post("/hook", c -> Reply.status(status.get())).start();
  }

  @AfterEach
  void tearDown() {
    server.close();
    exec.shutdownNow();
  }

  HttpSenderEventListenerProvider provider(String secret) {
    HttpSenderEventListenerProvider p = new HttpSenderEventListenerProvider(null, exec);
    Map<String, Object> config = new HashMap<>();
    config.put("targetUri", "http://localhost:" + port + "/hook");
    if (secret != null) config.put("sharedSecret", secret);
    p.setConfig(config);
    return p;
  }

  /** Formats to non-JSON text and refuses to retry 4xx. */
  static class TextFormatter implements WebhookPayloadFormatter {
    @Override
    public FormattedPayload format(List<ExtendedAdminEvent> events, Map<String, String> config) {
      String text = "event " + events.get(0).getId() + " é\n";
      return new FormattedPayload(
          text.getBytes(StandardCharsets.UTF_8), "text/plain", Map.of("X-Format", "text"));
    }

    @Override
    public boolean isRetryable(int httpStatus) {
      return httpStatus >= 500;
    }
  }

  @Test
  public void testDefaultPayloadIsSerializedEventAndSigned() throws Exception {
    HttpSenderEventListenerProvider p = provider("s3cret");
    Map<String, Object> event = Map.of("id", "e1", "type", "LOGIN");
    SenderEventListenerProvider.SenderTask task = p.new SenderTask(event, BackOff.STOP_BACKOFF);
    p.send(task);

    Captured c = server.await(WAIT, r -> true);
    String expected = JsonSerialization.writeValueAsString(event);
    assertThat(c.bodyAsString(), is(expected));
    assertThat(c.header("Content-Type"), is("application/json"));
    assertThat(
        c.header("X-Keycloak-Signature"),
        is(HttpSenderEventListenerProvider.calculateHmacSha(expected, "s3cret", "HmacSHA256")));
  }

  @Test
  public void testFormattedPayloadIsSentAndSignedVerbatim() throws Exception {
    HttpSenderEventListenerProvider p = provider("s3cret");
    ExtendedAdminEvent event = new ExtendedAdminEvent();
    event.setId("e2");
    TextFormatter formatter = new TextFormatter();
    SenderEventListenerProvider.SenderTask task = p.new SenderTask(event, BackOff.STOP_BACKOFF);
    task.setFormatter(formatter);
    task.setPayload(formatter.format(List.of(event), Map.of()));
    p.send(task);

    Captured c = server.await(WAIT, r -> true);
    byte[] expected = "event e2 é\n".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(expected, c.body());
    assertThat(c.header("Content-Type"), is("text/plain"));
    assertThat(c.header("X-Format"), is("text"));
    assertThat(
        c.header("X-Keycloak-Signature"),
        is(HttpSenderEventListenerProvider.calculateHmacSha(expected, "s3cret", "HmacSHA256")));
  }

  @Test
  public void testNoSignatureWithoutSecret() throws Exception {
    HttpSenderEventListenerProvider p = provider(null);
    p.send(p.new SenderTask(Map.of("id", "e3"), BackOff.STOP_BACKOFF));
    assertThat(server.await(WAIT, r -> true).header("X-Keycloak-Signature"), nullValue());
  }

  @Test
  public void testFormatterDecidesRetry() throws Exception {
    HttpSenderEventListenerProvider p = provider(null);
    ExtendedAdminEvent event = new ExtendedAdminEvent();
    event.setId("e4");
    TextFormatter formatter = new TextFormatter();
    SenderEventListenerProvider.SenderTask task = p.new SenderTask(event, BackOff.STOP_BACKOFF);
    task.setFormatter(formatter);
    task.setPayload(formatter.format(List.of(event), Map.of()));

    status.set(400);
    SenderEventListenerProvider.SenderException e =
        assertThrows(SenderEventListenerProvider.SenderException.class, () -> p.send(task));
    assertThat(e.isRetryable(), is(false));

    status.set(503);
    e = assertThrows(SenderEventListenerProvider.SenderException.class, () -> p.send(task));
    assertThat(e.isRetryable(), is(true));
  }

  @Test
  public void testDefaultRetriesEveryFailure() throws Exception {
    HttpSenderEventListenerProvider p = provider(null);
    status.set(400);
    SenderEventListenerProvider.SenderException e =
        assertThrows(
            SenderEventListenerProvider.SenderException.class,
            () -> p.send(p.new SenderTask(Map.of("id", "e5"), BackOff.STOP_BACKOFF)));
    assertThat(e.isRetryable(), is(true));
  }
}

package io.phasetwo.keycloak;

import com.github.xgp.http.server.Server;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A small HTTP server for tests that records every request (raw body bytes and headers) and answers
 * with a per-route reply, so tests can stand in for webhook receivers, SIEM ingestion endpoints and
 * OAuth2 token endpoints.
 */
public class RecordingServer implements AutoCloseable {

  /** A recorded request. Header names are case-insensitive. */
  public record Captured(
      String method, String path, String query, Map<String, String> headers, byte[] body) {
    public String header(String name) {
      return headers.get(name);
    }

    public String bodyAsString() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  public record Reply(int status, String contentType, String body) {
    public static Reply status(int status) {
      return new Reply(status, "text/plain", "");
    }

    public static Reply json(int status, String body) {
      return new Reply(status, "application/json", body);
    }
  }

  private final Server server;
  private final BlockingQueue<Captured> requests = new LinkedBlockingQueue<>();

  public RecordingServer(int port) {
    this.server = new Server(port);
  }

  /** Answer POSTs to {@code path} with {@code responder}, recording each request. */
  public RecordingServer post(String path, Function<Captured, Reply> responder) {
    server
        .router()
        .POST(
            path,
            (request, response) -> {
              byte[] body = request.stream().readAllBytes();
              Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
              request
                  .headers()
                  .forEach(
                      (k, v) -> {
                        if (k != null && v != null && !v.isEmpty()) headers.put(k, v.get(0));
                      });
              Captured c =
                  new Captured(
                      request.method(),
                      request.uri().getPath(),
                      request.uri().getRawQuery(),
                      headers,
                      body);
              requests.add(c);
              Reply reply = responder.apply(c);
              response.status(reply.status());
              response.contentType(reply.contentType());
              response.body(reply.body() == null ? "" : reply.body());
            });
    return this;
  }

  public RecordingServer start() throws Exception {
    server.start();
    return this;
  }

  /**
   * Wait for the next recorded request matching {@code filter}, discarding non-matching ones.
   *
   * @throws AssertionError on timeout
   */
  public Captured await(Duration timeout, Predicate<Captured> filter) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) throw new AssertionError("Timed out after " + timeout);
      Captured c = requests.poll(remaining, TimeUnit.NANOSECONDS);
      if (c != null && filter.test(c)) return c;
    }
  }

  /** Requests recorded so far that haven't been consumed by {@link #await}. */
  public List<Captured> drain() {
    List<Captured> l = new java.util.ArrayList<>();
    requests.drainTo(l);
    return l;
  }

  @Override
  public void close() {
    server.stop();
  }
}

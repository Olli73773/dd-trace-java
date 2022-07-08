package datadog.trace.bootstrap.instrumentation.api;

import datadog.trace.api.function.Consumer;
import java.io.IOException;

public interface PathwayContext {
  String PROPAGATION_KEY = "dd-pathway-ctx";
  String PROPAGATION_KEY_PRUNED = "dd-pathway-pruned";

  boolean isStarted();

  boolean isPruned();

  void start(Consumer<StatsPoint> pointConsumer);

  void setCheckpoint(String type, String group, String topic, Consumer<StatsPoint> pointConsumer);

  byte[] encode() throws IOException;
}

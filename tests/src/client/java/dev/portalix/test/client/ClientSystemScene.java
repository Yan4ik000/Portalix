package dev.portalix.test.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;

interface ClientSystemScene {
  record SkippedCase(String name, String reason) {}

  default void presentedFrame(long nanos) {}
  default List<SkippedCase> skipped() { return List.of(); }

  boolean tick();
  List<String> passed();
  CompletableFuture<Void> cleanup();
}

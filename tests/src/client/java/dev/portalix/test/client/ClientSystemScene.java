package dev.portalix.test.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;

interface ClientSystemScene {
  default void presentedFrame(long nanos) {}

  boolean tick();
  List<String> passed();
  CompletableFuture<Void> cleanup();
}

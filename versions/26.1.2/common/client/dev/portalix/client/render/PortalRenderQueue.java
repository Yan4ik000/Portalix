package dev.portalix.client.render;

import dev.portalix.client.platform.ClientPlatform.Phase;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;

public final class PortalRenderQueue {
  private final EnumMap<Phase, LinkedHashMap<Feature<?>, Batch<?>>> phases =
      new EnumMap<>(Phase.class);

  public interface Access {
    PortalRenderQueue portalix$renderQueue();
  }

  public interface Submission {
    void enqueue(PortalRenderQueue queue, Phase phase);
  }

  public static final class Feature<T> {
    private final Consumer<List<T>> draw;
    private final Consumer<T> release;

    public Feature(Consumer<List<T>> draw) {
      this(draw, submission -> {});
    }

    public Feature(Consumer<List<T>> draw, Consumer<T> release) {
      this.draw = draw;
      this.release = release;
    }
  }

  private static final class Batch<T> {
    private final Feature<T> feature;
    private final List<T> submissions = new ArrayList<>();

    private Batch(Feature<T> feature) {
      this.feature = feature;
    }

    private void draw() {
      if (submissions.isEmpty()) return;
      try {
        feature.draw.accept(submissions);
      } finally {
        clear();
      }
    }

    private void clear() {
      submissions.forEach(feature.release);
      submissions.clear();
    }
  }

  public static PortalRenderQueue of(OrderedSubmitNodeCollector collector) {
    if (collector instanceof SubmitNodeStorage storage) return of(storage.order(0));
    return ((Access) collector).portalix$renderQueue();
  }

  public static void drawPhase(
      SubmitNodeStorage storage, MultiBufferSource.BufferSource buffers, Phase phase) {
    var collections = storage.getSubmitsPerOrder().values();
    if (collections.stream().noneMatch(nodes -> of(nodes).hasSubmissions(phase))) return;

    buffers.endBatch();
    for (var nodes : collections) of(nodes).draw(phase);
  }

  public <T> void add(Phase phase, Feature<T> feature, T submission) {
    var batches = phases.computeIfAbsent(phase, key -> new LinkedHashMap<>());
    @SuppressWarnings("unchecked")
    Batch<T> batch = (Batch<T>) batches.computeIfAbsent(feature, key -> new Batch<>(feature));
    batch.submissions.add(submission);
  }

  public boolean hasSubmissions(Phase phase) {
    var batches = phases.get(phase);
    return batches != null && batches.values().stream().anyMatch(batch -> !batch.submissions.isEmpty());
  }

  public void draw(Phase phase) {
    var batches = phases.get(phase);
    if (batches != null) batches.values().forEach(Batch::draw);
  }

  public void clear() {
    phases.values().forEach(batches -> batches.values().forEach(Batch::clear));
  }
}

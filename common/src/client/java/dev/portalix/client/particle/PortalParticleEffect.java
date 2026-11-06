package dev.portalix.client.particle;

import dev.portalix.item.PortalixVariant;
import java.util.ArrayList;
import java.util.List;

public final class PortalParticleEffect {
  private final boolean orange;
  private final PortalixVariant variant;
  private final long identity;
  private final SourceParticleSystem edge;
  private SourceParticleSystem closing;
  private double updatedAt;

  public PortalParticleEffect(
      PortalixVariant variant, boolean orange, long identity, double born, double now) {
    this.variant = variant;
    this.orange = orange;
    this.identity = identity;
    edge = new SourceParticleSystem(variant, orange, identity, Math.max(0, now - born));
    updatedAt = now;
  }

  public void advance(double now) {
    if (now <= updatedAt) return;
    double elapsed = now - updatedAt;
    edge.advance(elapsed);
    if (closing != null) closing.advance(elapsed);
    updatedAt = now;
  }

  public void close(double now) {
    if (closing != null) return;
    advance(now);
    edge.close();
    closing = SourceParticleSystem.closing(variant, orange, identity);
  }

  public boolean finished() {
    return closing != null
        && edge.collections.stream().allMatch(collection -> collection.particles.isEmpty())
        && closing.collections.stream().allMatch(collection -> collection.particles.isEmpty());
  }

  List<SourceParticleSystem.Collection> collections() {
    if (closing == null) return edge.collections;
    List<SourceParticleSystem.Collection> collections = new ArrayList<>(edge.collections);
    collections.addAll(closing.collections);
    return collections;
  }
}

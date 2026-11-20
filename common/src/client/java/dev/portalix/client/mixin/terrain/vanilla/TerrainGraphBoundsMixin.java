package dev.portalix.client.mixin.terrain.vanilla;

import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import net.minecraft.client.renderer.SectionOcclusionGraph.GraphStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GraphStorage.class)
abstract class TerrainGraphBoundsMixin implements PortalOcclusionContext.SnapshotAccess {
  @Unique private final PortalOcclusionContext.Snapshot portalix$snapshot = PortalOcclusionContext.current();

  @Override
  public PortalOcclusionContext.Snapshot portalix$occlusionSnapshot() {
    return portalix$snapshot;
  }
}

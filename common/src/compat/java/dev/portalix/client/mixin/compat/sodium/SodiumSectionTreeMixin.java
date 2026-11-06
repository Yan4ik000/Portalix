package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumCullRequest;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(value = SectionTree.class, remap = false)
abstract class SodiumSectionTreeMixin implements SodiumCullRequest.SnapshotAccess {
  @Unique private final SodiumCullRequest.Snapshot portalix$snapshot =
      SodiumCullRequest.Snapshot.ACTIVE.isBound() ? SodiumCullRequest.Snapshot.ACTIVE.get() : null;

  @Override
  public SodiumCullRequest.Snapshot portalix$cullSnapshot() {
    return portalix$snapshot;
  }
}

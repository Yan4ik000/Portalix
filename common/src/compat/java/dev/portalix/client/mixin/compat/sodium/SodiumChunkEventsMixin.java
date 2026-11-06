package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.SceneChunkEventAccess;
import dev.portalix.client.portal.scene.SceneChunkEvents;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker", remap = false)
public abstract class SodiumChunkEventsMixin implements SceneChunkEventAccess {
  @Unique private final SceneChunkEvents portalix$events = new SceneChunkEvents();

  @WrapOperation(
      method = "updateMerged",
      at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;add(J)Z"))
  private boolean portalix$loaded(LongOpenHashSet ready, long chunk, Operation<Boolean> original) {
    boolean changed = original.call(ready, chunk);
    if (changed) portalix$events.added(chunk);
    return changed;
  }

  @WrapOperation(
      method = "updateMerged",
      at =
          @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;remove(J)Z"))
  private boolean portalix$unloaded(
      LongOpenHashSet ready, long chunk, Operation<Boolean> original) {
    boolean changed = original.call(ready, chunk);
    if (changed) portalix$events.removed(chunk);
    return changed;
  }

  @Override
  public SceneChunkEvents portalix$chunkEvents() {
    return portalix$events;
  }
}

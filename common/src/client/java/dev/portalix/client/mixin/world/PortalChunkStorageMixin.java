package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.minecraft.MinecraftPortalChunks;
import dev.portalix.client.world.PortalChunkStorageAccess;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.Storage.class)
abstract class PortalChunkStorageMixin implements PortalChunkStorageAccess {
  @Shadow @Final @Mutable private LongOpenHashSet[] addedEmptySections;
  @Shadow @Final @Mutable private LongOpenHashSet[] removedEmptySections;
  @Shadow @Final @Mutable private LongOpenHashSet[] addedLoadedChunks;
  @Shadow @Final @Mutable private LongOpenHashSet[] removedLoadedChunks;
  @Shadow private int updatingSetsIndex;
  @Shadow private int chunkCount;
  @Shadow @Final ClientChunkCache this$0;

  @Override
  @Invoker("inRange")
  public abstract boolean portalix$inRange(int x, int z);

  @Override
  @Invoker("getIndex")
  public abstract int portalix$index(int x, int z);

  @Override
  @Accessor("chunks")
  public abstract AtomicReferenceArray<LevelChunk> portalix$slots();

  @Override
  public void portalix$inheritTracking(PortalChunkStorageAccess previous) {
    // The arrays belong to the loaded columns, not to the dimensions of the ring.
    var old = (PortalChunkStorageMixin) previous;
    addedEmptySections = old.addedEmptySections;
    removedEmptySections = old.removedEmptySections;
    addedLoadedChunks = old.addedLoadedChunks;
    removedLoadedChunks = old.removedLoadedChunks;
    updatingSetsIndex = old.updatingSetsIndex;
    chunkCount = old.chunkCount;
  }

  @WrapOperation(
      method = "replace",
      at = @At(value = "INVOKE",
          target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;getAndSet(ILjava/lang/Object;)Ljava/lang/Object;"),
      require = 1, allow = 1)
  private Object portalix$place(AtomicReferenceArray<LevelChunk> slots, int index,
      Object value, Operation<Object> original) {
    var chunk = (LevelChunk) value;
    var chunks = ((MinecraftPortalChunks.Access) this$0).portalix$chunks();
    var pos = chunk.getPos();
    if (portalix$inRange(pos.x(), pos.z())) {
      var displaced = slots.get(index);
      // A ring slot can still hold a delivered chunk from the previous center.
      if (displaced != null) chunks.retain(displaced);
      original.call(slots, index, chunk);
    } else {
      chunks.retain(chunk);
    }
    return null;
  }

  @WrapOperation(
      method = "drop",
      at = @At(value = "INVOKE",
          target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;compareAndSet(ILjava/lang/Object;Ljava/lang/Object;)Z"),
      require = 1, allow = 1)
  private boolean portalix$remove(AtomicReferenceArray<LevelChunk> slots, int index,
      Object old, Object replacement, Operation<Boolean> original) {
    if (slots.get(index) == old) return original.call(slots, index, old, replacement);
    return ((MinecraftPortalChunks.Access) this$0).portalix$chunks().forget((LevelChunk) old);
  }

  @WrapOperation(
      method = "onSectionEmptinessChanged",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;inRange(II)Z"),
      require = 1, allow = 1)
  private boolean portalix$loadedSection(ClientChunkCache.Storage storage, int x, int z,
      Operation<Boolean> original) {
    return this$0.getChunk(x, z, false) != null;
  }

  @WrapOperation(
      method = {"onChunkAdded", "onChunkRemoved", "refreshEmptySections", "onSectionEmptinessChanged"},
      at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;add(J)Z"),
      require = 8, allow = 8)
  private boolean portalix$lastChange(LongOpenHashSet changes, long key, Operation<Boolean> original) {
    int index = updatingSetsIndex;
    if (changes == addedEmptySections[index]) {
      removedEmptySections[index].remove(key);
    } else if (changes == removedEmptySections[index]) {
      addedEmptySections[index].remove(key);
    } else if (changes == addedLoadedChunks[index]) {
      removedLoadedChunks[index].remove(key);
    } else if (changes == removedLoadedChunks[index]) {
      addedLoadedChunks[index].remove(key);
    }
    return original.call(changes, key);
  }

  @Inject(method = "onChunkAdded", at = @At("TAIL"))
  private void portalix$added(LevelChunk chunk, CallbackInfo ci) {
    ((MinecraftPortalChunks.Access) this$0).portalix$chunks().added();
  }

  @Inject(method = "onChunkRemoved", at = @At("TAIL"))
  private void portalix$removed(LevelChunk chunk, CallbackInfo ci) {
    ((MinecraftPortalChunks.Access) this$0).portalix$chunks().removed(chunk.getPos());
  }
}

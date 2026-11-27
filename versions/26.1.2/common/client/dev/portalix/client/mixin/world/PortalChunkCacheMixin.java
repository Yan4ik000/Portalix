package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.minecraft.MinecraftPortalChunks;
import dev.portalix.client.world.PortalChunkStorageAccess;
import dev.portalix.client.portal.terrain.PortalTerrainColumns;
import dev.portalix.client.world.RetainedChunkAccess;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
abstract class PortalChunkCacheMixin implements RetainedChunkAccess, MinecraftPortalChunks.Access {
  @Shadow @Final private ClientLevel level;
  @Shadow @Final private LevelChunk emptyChunk;
  @Shadow private volatile ClientChunkCache.Storage storage;

  @Unique private MinecraftPortalChunks portalix$chunks;

  @Inject(method = "<init>", at = @At("TAIL"))
  private void portalix$terrainIndex(CallbackInfo ci) {
    portalix$chunks = new MinecraftPortalChunks(level);
  }

  @Override
  public MinecraftPortalChunks portalix$chunks() {
    return portalix$chunks;
  }

  @Override
  public PortalTerrainColumns portalix$terrainColumns() {
    return portalix$chunks.columns();
  }

  @Override
  public List<LevelChunk> portalix$retainedChunks() {
    return portalix$chunks.loaded(((PortalChunkStorageAccess) (Object) storage).portalix$slots());
  }

  /**
   * @author Portalix
   * @reason Read retained columns and use one ring for both indexing and lookup during a resize.
   */
  @Overwrite
  public @Nullable LevelChunk getChunk(int x, int z, ChunkStatus status, boolean load) {
    var current = storage;
    var access = (PortalChunkStorageAccess) (Object) current;
    var chunk = portalix$chunks.find(access.portalix$slots().get(access.portalix$index(x, z)), x, z);
    if (chunk != null) return chunk;
    return load ? emptyChunk : null;
  }

  @WrapOperation(
      method = {"replaceWithPacketData", "replaceBiomes", "drop"},
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;inRange(II)Z"),
      require = 3, allow = 3)
  private boolean portalix$retainedRange(ClientChunkCache.Storage storage, int x, int z,
      Operation<Boolean> original) {
    return true;
  }

  @WrapOperation(
      method = {"replaceWithPacketData", "replaceBiomes"},
      at = @At(value = "INVOKE",
          target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;get(I)Ljava/lang/Object;"),
      require = 2, allow = 2)
  private Object portalix$packetLookup(AtomicReferenceArray<LevelChunk> slots, int index,
      Operation<Object> original, @Local(argsOnly = true, ordinal = 0) int x,
      @Local(argsOnly = true, ordinal = 1) int z) {
    return portalix$chunks.find((LevelChunk) original.call(slots, index), x, z);
  }

  @WrapOperation(
      method = "drop",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;getChunk(I)Lnet/minecraft/world/level/chunk/LevelChunk;"),
      require = 1, allow = 1)
  private LevelChunk portalix$forgetLookup(ClientChunkCache.Storage storage, int index,
      Operation<LevelChunk> original, ChunkPos pos) {
    return portalix$chunks.find(original.call(storage, index), pos.x(), pos.z());
  }

  @Inject(method = "replaceWithPacketData",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/multiplayer/ClientLevel;onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V"),
      require = 1, allow = 1)
  private void portalix$indexColumn(CallbackInfoReturnable<LevelChunk> ci, @Local LevelChunk chunk) {
    portalix$chunks.updateColumn(chunk);
  }

  @Inject(method = "onSectionEmptinessChanged", at = @At("TAIL"))
  private void portalix$section(int x, int y, int z, boolean empty, CallbackInfo ci) {
    portalix$chunks.sectionChanged(x, y, z, empty);
  }

  @WrapOperation(
      method = "updateViewRadius",
      at = @At(value = "INVOKE",
          target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;get(I)Ljava/lang/Object;"),
      require = 1, allow = 1)
  private Object portalix$keepOutsideRadius(AtomicReferenceArray<LevelChunk> slots, int index,
      Operation<Object> original, @Local(ordinal = 0) ClientChunkCache.Storage resized) {
    var chunk = (LevelChunk) original.call(slots, index);
    if (chunk != null) {
      var pos = chunk.getPos();
      if (!((PortalChunkStorageAccess) (Object) resized).portalix$inRange(pos.x(), pos.z())) {
        portalix$chunks.retain(chunk);
        return null;
      }
    }
    return chunk;
  }

  @WrapOperation(
      method = "updateViewRadius",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;replace(ILnet/minecraft/world/level/chunk/LevelChunk;)V"),
      require = 1, allow = 1)
  private void portalix$transferSlot(ClientChunkCache.Storage resized, int index, LevelChunk chunk,
      Operation<Void> original) {
    ((PortalChunkStorageAccess) (Object) resized).portalix$slots().set(index, chunk);
  }

  @WrapOperation(
      method = "updateViewRadius",
      at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.PUTFIELD,
          target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;storage:Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"),
      require = 1, allow = 1)
  private void portalix$publishResize(ClientChunkCache cache, ClientChunkCache.Storage resized,
      Operation<Void> original) {
    ((PortalChunkStorageAccess) (Object) resized).portalix$inheritTracking((PortalChunkStorageAccess) (Object) storage);
    original.call(cache, resized);
  }
}

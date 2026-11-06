package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.client.compat.minecraft.MinecraftPortalChunks;
import dev.portalix.client.portal.terrain.PortalTerrainColumns;
import dev.portalix.client.world.RetainedChunkAccess;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
abstract class PortalChunkCacheMixin implements RetainedChunkAccess {
  @Shadow @Final private ClientLevel level;
  @Shadow @Final private LevelChunk emptyChunk;

  @Unique private MinecraftPortalChunks portalix$chunks;

  @Inject(method = "<init>", at = @At("TAIL"))
  private void portalix$terrainIndex(CallbackInfo ci) {
    portalix$chunks = new MinecraftPortalChunks((ClientChunkCache) (Object) this, level, emptyChunk);
  }

  @Override
  public PortalTerrainColumns portalix$terrainColumns() {
    return portalix$chunks.columns();
  }

  @Override
  public List<LevelChunk> portalix$retainedChunks() {
    return portalix$chunks.retained();
  }

  @WrapMethod(
      method =
          "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;")
  private LevelChunk portalix$get(
      int x, int z, ChunkStatus status, boolean load, Operation<LevelChunk> original) {
    return portalix$chunks.get(x, z, load);
  }

  @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
  private void portalix$receive(
      int x,
      int z,
      FriendlyByteBuf buffer,
      Map<Heightmap.Types, long[]> heightmaps,
      Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities,
      CallbackInfoReturnable<LevelChunk> callback) {
    callback.setReturnValue(portalix$chunks.receive(x, z, buffer, heightmaps, blockEntities));
  }

  @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
  private void portalix$biomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo callback) {
    portalix$chunks.replaceBiomes(x, z, buffer);
    callback.cancel();
  }

  @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
  private void portalix$drop(ChunkPos pos, CallbackInfo callback) {
    portalix$chunks.drop(pos);
    callback.cancel();
  }

  @Inject(method = "updateViewRadius", at = @At("HEAD"), cancellable = true)
  private void portalix$radius(int radius, CallbackInfo callback) {

    callback.cancel();
  }

  @Inject(method = "getLoadedChunksCount", at = @At("HEAD"), cancellable = true)
  private void portalix$count(CallbackInfoReturnable<Integer> callback) {
    callback.setReturnValue(portalix$chunks.size());
  }

  @Inject(method = "gatherStats", at = @At("HEAD"), cancellable = true)
  private void portalix$stats(CallbackInfoReturnable<String> callback) {
    callback.setReturnValue("portal union: " + portalix$chunks.size());
  }

  @Inject(method = "onSectionEmptinessChanged", at = @At("HEAD"), cancellable = true)
  private void portalix$section(int x, int y, int z, boolean empty, CallbackInfo callback) {
    portalix$chunks.sectionChanged(x, y, z, empty);
    callback.cancel();
  }
}

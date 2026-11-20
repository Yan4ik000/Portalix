package dev.portalix.mixin.world.snapshot;

import dev.portalix.world.snapshot.ChunkPaletteSnapshots;
import dev.portalix.world.snapshot.LocalChunkPalettes;
import dev.portalix.world.snapshot.PaletteSnapshot;
import dev.portalix.world.snapshot.PaletteSnapshotAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundLevelChunkPacketData.class)
abstract class PortalLocalChunkSnapshotMixin implements ChunkPaletteSnapshots {
  @Unique private @Nullable List<PaletteSnapshot<BlockState>> portalix$palettes;

  @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("RETURN"))
  @SuppressWarnings("unchecked")
  private void portalix$localSnapshots(LevelChunk chunk, CallbackInfo ci) {
    if (!LocalChunkPalettes.sending()) return;
    var snapshots = new ArrayList<PaletteSnapshot<BlockState>>(chunk.getSections().length);
    for (var section : chunk.getSections())
      snapshots.add(
          ((PaletteSnapshotAccess<BlockState>) section.getStates()).portalix$packetSnapshot());
    portalix$palettes = List.copyOf(snapshots);
  }

  @Inject(method = "<init>(Lnet/minecraft/network/RegistryFriendlyByteBuf;II)V", at = @At("RETURN"))
  private void portalix$receivedSnapshots(
      RegistryFriendlyByteBuf buffer, int x, int z, CallbackInfo ci) {
    portalix$palettes = LocalChunkPalettes.decoding();
  }

  @Override
  public @Nullable List<PaletteSnapshot<BlockState>> portalix$palettes() {
    return portalix$palettes;
  }
}

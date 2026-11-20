package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.world.snapshot.LocalChunkPalettes;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelChunk.class)
abstract class PortalLocalChunkReadMixin {
  @WrapMethod(method = "replaceWithPacketData")
  private void portalix$localStates(
      int x, int z, ClientboundLevelChunkPacketData packet, Operation<Void> original) {
    LocalChunkPalettes.read(packet, () -> original.call(x, z, packet));
  }
}

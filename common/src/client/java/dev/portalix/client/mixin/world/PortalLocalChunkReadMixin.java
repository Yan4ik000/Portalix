package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.world.snapshot.LocalChunkPalettes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPacketListener.class)
abstract class PortalLocalChunkReadMixin {
  @WrapMethod(method = "updateLevelChunk")
  private void portalix$localStates(
      int x, int z, ClientboundLevelChunkPacketData packet, Operation<Void> original) {
    LocalChunkPalettes.read(packet, () -> original.call(x, z, packet));
  }
}

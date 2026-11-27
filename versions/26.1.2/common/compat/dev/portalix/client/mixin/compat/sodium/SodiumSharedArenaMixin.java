package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.SodiumSharedArenaRetirement;
import net.caffeinemc.mods.sodium.client.gl.arena.BufferArena;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = BufferArena.class, remap = false)
abstract class SodiumSharedArenaMixin implements SodiumSharedArenaRetirement {
  @Unique private boolean portalix$recycleOnRetirement;

  @Override
  public void portalix$recycleOnRetirement(boolean recycle) {
    portalix$recycleOnRetirement = recycle;
  }

  @WrapOperation(
      method = "deleteSingleOwner",
      at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;deleteBuffer(Lnet/caffeinemc/mods/sodium/client/gl/buffer/GlBuffer;)V"))
  private void portalix$returnRetiredBuffer(CommandList commands, GlBuffer buffer, Operation<Void> operation) {
    if (portalix$recycleOnRetirement) {
      var parent = ((BufferArenaAccessor) this).portalix$parent();
      ((ArenaAggregatorAccessor) parent).portalix$releaseBufferForReuse(commands, (GlMutableBuffer) buffer);
    } else {
      operation.call(commands, buffer);
    }
  }
}

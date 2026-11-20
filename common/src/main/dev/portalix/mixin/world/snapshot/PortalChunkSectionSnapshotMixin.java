package dev.portalix.mixin.world.snapshot;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.world.snapshot.LocalChunkPalettes;
import dev.portalix.world.snapshot.PaletteSnapshotAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunkSection.class)
abstract class PortalChunkSectionSnapshotMixin {
  @Definition(
      id = "states",
      field =
          "Lnet/minecraft/world/level/chunk/LevelChunkSection;states:Lnet/minecraft/world/level/chunk/PalettedContainer;")
  @Definition(
      id = "read",
      method =
          "Lnet/minecraft/world/level/chunk/PalettedContainer;read(Lnet/minecraft/network/FriendlyByteBuf;)V")
  @Expression("this.states.read(?)")
  @WrapOperation(
      method = "read",
      at = @At("MIXINEXTRAS:EXPRESSION"),
      allow = 1)
  @SuppressWarnings("unchecked")
  private void portalix$sharedStates(
      PalettedContainer<BlockState> states, FriendlyByteBuf buffer, Operation<Void> original) {
    var snapshot = LocalChunkPalettes.next();
    if (snapshot == null
        || !((PaletteSnapshotAccess<BlockState>) states).portalix$readSnapshot(buffer, snapshot))
      original.call(states, buffer);
  }
}

package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumTopoSorterAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData$DynamicTopoSorter",
    remap = false)
abstract class SodiumTopoSorterMixin implements SodiumTopoSorterAccess {
  @Shadow private boolean directTrigger;
  @Shadow private boolean GFNITrigger;
  @Shadow private int consecutiveTopoSortFailuresNew;
  @Shadow @Final @Mutable private int consecutiveTopoSortFailures;
  @Shadow @Final @Mutable private boolean isDirectTrigger;

  @Override
  public State portalix$state() {
    return new State(GFNITrigger, directTrigger, consecutiveTopoSortFailuresNew);
  }

  @Override
  public void portalix$state(State state, boolean crossedPlane) {
    GFNITrigger = state.planes();
    directTrigger = state.distance();
    consecutiveTopoSortFailures = state.failures();
    consecutiveTopoSortFailuresNew = state.failures();
    isDirectTrigger = state.distance() && !crossedPlane;
  }
}

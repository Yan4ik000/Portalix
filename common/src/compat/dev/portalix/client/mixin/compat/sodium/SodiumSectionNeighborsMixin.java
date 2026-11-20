package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumSectionNeighbors;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.GraphDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(value = RenderSection.class, remap = false)
abstract class SodiumSectionNeighborsMixin implements SodiumSectionNeighbors {
  @Shadow private int adjacentMask;
  @Shadow public RenderSection adjacentDown;
  @Shadow public RenderSection adjacentUp;
  @Shadow public RenderSection adjacentNorth;
  @Shadow public RenderSection adjacentSouth;
  @Shadow public RenderSection adjacentWest;
  @Shadow public RenderSection adjacentEast;

  @Override
  public void portalix$disconnect(int retainedDirections) {
    var section = (RenderSection) (Object) this;
    int retained = retainedDirections & adjacentMask;
    while (retained != 0) {
      int direction = Integer.numberOfTrailingZeros(retained);
      retained &= retained - 1;
      section.getAdjacent(direction).setAdjacentNode(GraphDirection.opposite(direction), null);
    }
    adjacentDown = null;
    adjacentUp = null;
    adjacentNorth = null;
    adjacentSouth = null;
    adjacentWest = null;
    adjacentEast = null;
    adjacentMask = 0;
  }
}

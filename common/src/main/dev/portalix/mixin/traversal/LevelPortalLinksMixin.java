package dev.portalix.mixin.traversal;

import dev.portalix.portal.PortalTopology;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public abstract class LevelPortalLinksMixin implements PortalTopology.Access {
  @Unique private final PortalTopology portalix$links = new PortalTopology();

  @Override
  public PortalTopology portalix$links() {
    return portalix$links;
  }
}

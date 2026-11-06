package dev.portalix.neoforge.mixin;

import dev.portalix.neoforge.NeoForgePortalData;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
abstract class PlayerPortalDataMixin implements PortalPassagePose.Access {
  @Override
  public float portalix$poseScale() {
    return NeoForgePortalData.poseScale((Player) (Object) this);
  }

  @Override
  public void portalix$poseScale(float scale) {
    NeoForgePortalData.poseScale((Player) (Object) this, scale);
  }

  @Override
  public boolean portalix$poseField(int id) {
    return false;
  }
}

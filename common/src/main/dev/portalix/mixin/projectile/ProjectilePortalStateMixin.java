package dev.portalix.mixin.projectile;

import dev.portalix.portal.traversal.ProjectilePortalMovement;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Projectile.class)
public abstract class ProjectilePortalStateMixin implements ProjectilePortalMovement.Access {
  @Unique
  private final ProjectilePortalMovement.State portalix$projectile =
      new ProjectilePortalMovement.State();

  @Override
  public ProjectilePortalMovement.State portalix$projectileState() {
    return portalix$projectile;
  }
}

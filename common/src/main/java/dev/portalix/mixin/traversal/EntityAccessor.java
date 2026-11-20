package dev.portalix.mixin.traversal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
  @Accessor("firstTick")
  boolean portalix$firstTick();

  @Accessor("wasTouchingWater")
  void portalix$wasTouchingWater(boolean immersed);

  @Invoker("doWaterSplashEffect")
  void portalix$waterSplash();

  @Accessor("wasEyeInWater")
  void portalix$wasEyeInWater(boolean immersed);

  @Accessor("fluidInteraction")
  EntityFluidInteraction portalix$fluidInteraction();
}

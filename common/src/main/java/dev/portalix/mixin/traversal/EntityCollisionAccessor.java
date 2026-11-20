package dev.portalix.mixin.traversal;

import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityCollisionAccessor {
  @Invoker("collide")
  Vec3 portalix$collide(Vec3 displacement);

  @Invoker("limitPistonMovement")
  Vec3 portalix$limitPistonMovement(Vec3 displacement);

  @Accessor("movementThisTick")
  ArrayDeque<Entity.Movement> portalix$movementRecords();

  @Invoker("addMovementThisTick")
  void portalix$recordMovement(Entity.Movement movement);

  @Invoker("applyEffectsFromBlocks")
  void portalix$applyMovementEffects(List<Entity.Movement> movements);
}

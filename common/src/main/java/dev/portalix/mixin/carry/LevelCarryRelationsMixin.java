package dev.portalix.mixin.carry;

import dev.portalix.carry.CarryBindings;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public abstract class LevelCarryRelationsMixin implements CarryBindings.Access {
  @Unique private final CarryBindings portalix$relations = new CarryBindings();

  @Override
  public CarryBindings portalix$relations() {
    return this.portalix$relations;
  }
}

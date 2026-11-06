package dev.portalix.client.mixin.entity;

import dev.portalix.client.portal.entity.PortalEntityRenderStateAccess;
import dev.portalix.client.portal.entity.PortalEntitySlice;
import java.util.List;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class PortalEntityStateMixin implements PortalEntityRenderStateAccess {
  @Unique private @Nullable List<PortalEntitySlice> portalix$slices;
  @Unique private Quaternionf portalix$carryRotation;
  @Unique private Quaternionf portalix$modelRotation;

  @Override
  public Quaternionf portalix$modelRotation() {
    return portalix$modelRotation;
  }

  @Override
  public void portalix$modelRotation(Quaternionf rotation) {
    portalix$modelRotation = rotation;
  }

  @Override
  public Quaternionf portalix$carryRotation() {
    return portalix$carryRotation;
  }

  @Override
  public void portalix$carryRotation(Quaternionf rotation) {
    portalix$carryRotation = rotation;
  }

  @Override
  public @Nullable List<PortalEntitySlice> portalix$slices() {
    return portalix$slices;
  }

  @Override
  public void portalix$slices(@Nullable List<PortalEntitySlice> slices) {
    portalix$slices = slices;
  }
}

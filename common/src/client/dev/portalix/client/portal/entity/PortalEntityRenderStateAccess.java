package dev.portalix.client.portal.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public interface PortalEntityRenderStateAccess {
  @Nullable List<PortalEntitySlice> portalix$slices();

  void portalix$slices(@Nullable List<PortalEntitySlice> slices);

  Quaternionf portalix$carryRotation();

  void portalix$carryRotation(Quaternionf rotation);

  void portalix$applyModelRotation(PoseStack poses);

  void portalix$modelRotation(Quaternionf rotation);
}

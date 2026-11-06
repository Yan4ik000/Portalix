package dev.portalix.portal.traversal;

import dev.portalix.portal.geometry.PortalTransform;
import net.minecraft.world.entity.item.ItemEntity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public final class PortalItemRotation {
  private PortalItemRotation() {}

  public static Quaternionf rotation(ItemEntity item) {
    return new Quaternionf(((Access) item).portalix$rotation());
  }

  public static void crossed(ItemEntity item, PortalTransform transform) {
    if (!item.level().isClientSide())
      ((Access) item).portalix$rotation(rotation(item).premul(transform.rotation()).normalize());
  }

  public interface Access {
    Quaternionfc portalix$rotation();

    void portalix$rotation(Quaternionfc rotation);
  }
}

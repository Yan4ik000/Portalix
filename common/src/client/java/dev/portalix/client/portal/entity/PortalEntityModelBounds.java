package dev.portalix.client.portal.entity;

import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class PortalEntityModelBounds {
  private static final ItemModelBounds ITEM = new ItemModelBounds();

  private PortalEntityModelBounds() {}

  static void reload() {
    ITEM.reload();
  }

  static double itemPadding(Entity entity) {
    if (entity instanceof ItemEntity item) {
      Minecraft.getInstance()
          .getItemModelResolver()
          .updateForNonLiving(ITEM, item.getItem(), ItemDisplayContext.GROUND, item);

      return 2 * modelRadius(ItemDisplayContext.GROUND);
    }
    if (!(entity instanceof LivingEntity living)) return 0;
    boolean right = living.getMainArm() == HumanoidArm.RIGHT;
    double radius =
        Math.max(
            itemRadius(living, living.getMainHandItem(), right),
            itemRadius(living, living.getOffhandItem(), !right));

    var body = PortalPassagePose.interactionBounds(entity);
    double reach = Math.max(body.getYsize(), Math.max(body.getXsize(), body.getZsize()));
    return reach + radius * living.getScale();
  }

  private static double itemRadius(LivingEntity entity, ItemStack item, boolean right) {
    if (item.isEmpty()) return 0;
    var context =
        right
            ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    Minecraft.getInstance().getItemModelResolver().updateForLiving(ITEM, item, context, entity);
    return modelRadius(context);
  }

  private static double modelRadius(ItemDisplayContext context) {
    var bounds = ITEM.bounds(context);
    double x = Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX));
    double y = Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY));
    double z = Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ));
    return Math.sqrt(x * x + y * y + z * z);
  }
}

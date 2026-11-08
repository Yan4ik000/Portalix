package dev.portalix.client.portal.entity;

import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class PortalEntityModelBounds {
  private static final ItemModelBounds ITEM = new ItemModelBounds();
  private static final Map<ItemModel, double[]> FIXED_RADII = new IdentityHashMap<>();
  private static final int DISPLAY_CONTEXTS = ItemDisplayContext.values().length;

  private PortalEntityModelBounds() {}

  static void reload() {
    ITEM.reload();
    FIXED_RADII.clear();
  }

  static double itemPadding(Entity entity) {
    if (entity instanceof ItemEntity item)
      return 2 * itemRadius(item, item.getItem(), ItemDisplayContext.GROUND);
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
    return itemRadius(entity, item, right
        ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
  }

  private static double itemRadius(Entity entity, ItemStack item, ItemDisplayContext context) {
    if (item.isEmpty()) return 0;
    var id = item.get(DataComponents.ITEM_MODEL);
    if (id == null) return 0;
    var client = Minecraft.getInstance();
    var model = client.getModelManager().getItemModel(id);
    double[] radii = null;
    if (model.getClass() == CuboidItemModelWrapper.class) {
      radii = FIXED_RADII.computeIfAbsent(model, ignored -> {
        var values = new double[DISPLAY_CONTEXTS];
        Arrays.fill(values, -1);
        return values;
      });
      double cached = radii[context.ordinal()];
      if (cached >= 0) return cached;
    }

    var resolver = client.getItemModelResolver();
    if (entity instanceof LivingEntity living) resolver.updateForLiving(ITEM, item, context, living);
    else resolver.updateForNonLiving(ITEM, item, context, entity);
    var bounds = ITEM.bounds(context);
    double x = Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX));
    double y = Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY));
    double z = Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ));
    double radius = Math.sqrt(x * x + y * y + z * z);
    if (radii != null) radii[context.ordinal()] = radius;
    return radius;
  }
}

package dev.portalix.client.gun.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.compat.iris.IrisDrawContext;
import dev.portalix.client.gun.animation.GunAnimator;
import dev.portalix.client.gun.model.GunModels;
import dev.portalix.client.gun.model.GunPose;
import dev.portalix.client.particle.CarryParticleRenderer;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class PortalixItemRenderer
    implements SpecialModelRenderer<PortalixItemRenderer.Argument> {
  public static final PortalixItemRenderer INSTANCE = new PortalixItemRenderer();

  @SuppressWarnings("try")
  public static void hand(
      PoseStack pose,
      SubmitNodeCollector collector,
      int light,
      float partial,
      boolean left,
      ItemStack stack) {
    try (var ignored = IrisDrawContext.forItem(stack.getItem())) {
      PortalixVariant variant = PortalixVariant.of(stack);
      var gun = PortalixIdentity.get(stack);
      PortalixClient.localAnimation()
          .synchronizePortals(
              PortalixClient.portals().activeMask(gun),
              PortalixClient.portals().secondaryActive(gun));
      float[] sample = PortalixClient.localAnimation().sample(partial);
      Matrix4f[] matrices = GunModels.view().skinMatrices(sample);
      pose.pushPose();
      GunModels.view().applyPresentation(GunPose.HAND, pose, left);
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
        float time = mc.player.tickCount + partial;
        double speed = Math.min(mc.player.getDeltaMovement().horizontalDistance() * 4.0, 1.0);
        pose.translate(
            Math.sin(time * 0.4) * speed * 0.07, Math.sin(time * 0.8) * speed * 0.045, 0.0);
      }

      GunModels.view().submitAnimated(pose, collector, sample, matrices, light, variant);
      GunGlowRenderer.submit(
          GunModels.view(),
          matrices,
          pose,
          collector,
          variant,
          PortalixClient.localAnimation().orange(),
          PortalixClient.localAnimation().hasEnergy());
      CarryParticleRenderer.hand(GunModels.view(), matrices, pose, collector, partial);
      pose.popPose();
    }
  }

  @SuppressWarnings("try")
  public void submit(
      Argument argument,
      PoseStack pose,
      SubmitNodeCollector collector,
      int light,
      int overlay,
      boolean foil,
      int outline) {
    try (var ignored = IrisDrawContext.forItem(argument.item())) {
      ItemDisplayContext context = argument.context();
      pose.pushPose();
      if (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
          && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
        GunModels.world()
            .applyPresentation(
                context == ItemDisplayContext.GUI
                    ? GunPose.ICON
                    : (context == ItemDisplayContext.GROUND ? GunPose.GROUND : GunPose.ITEM),
                pose,
                false);
        Matrix4f[] matrices = GunModels.world().idleMatrices();
        GunModels.world()
            .submit(
                pose,
                collector,
                matrices,
                light,
                context == ItemDisplayContext.GUI,
                argument.variant());
      } else {
        GunAnimator animation = PortalixClient.animation(argument.playerId());
        Matrix4f[] matrices = GunModels.world().idleMatrices();
        Vector3f grip = GunModels.world().grip(matrices);
        GunModels.world().applyPresentation(GunPose.WORLD_HAND, pose, context.leftHand());
        GunModels.world().alignGrip(pose, grip);
        GunModels.world().submit(pose, collector, matrices, light, false, argument.variant());
        GunGlowRenderer.submit(
            GunModels.world(),
            matrices,
            pose,
            collector,
            argument.variant(),
            animation.orange(),
            animation.hasEnergy());
        CarryParticleRenderer.world(
            argument.playerId(), GunModels.world(), matrices, pose, collector);
      }

      pose.popPose();
    }
  }

  public void getExtents(Consumer<Vector3fc> out) {
    GunModels.world().getExtents(out);
  }

  public Argument extractArgument(ItemStack stack) {
    return new Argument(ItemDisplayContext.NONE, -1, PortalixVariant.of(stack), stack.getItem());
  }

  public record Argument(
      ItemDisplayContext context, int playerId, PortalixVariant variant, Item item) {}
}

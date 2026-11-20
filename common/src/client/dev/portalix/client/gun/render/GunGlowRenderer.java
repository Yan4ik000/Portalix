package dev.portalix.client.gun.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.gun.model.GunModel;
import dev.portalix.client.particle.GunIndicatorRenderer;
import dev.portalix.client.render.BillboardOrientation;
import dev.portalix.item.PortalixVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;

final class GunGlowRenderer {
  static void submit(
      GunModel mesh,
      Matrix4f[] matrices,
      PoseStack pose,
      SubmitNodeCollector collector,
      PortalixVariant variant,
      boolean orange,
      boolean energized) {
    if (!energized) {
      return;
    }
    Matrix4f modelToView =
        mesh.isWorld()
            ? RenderSystem.getModelViewMatrixCopy().mul(pose.last().pose())
            : new Matrix4f(pose.last().pose());
    BillboardOrientation facing = new BillboardOrientation(modelToView);
    Minecraft mc = Minecraft.getInstance();
    float phase =
        mc.player == null
            ? 0.0F
            : (mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))
                / 20.0F;
    GunIndicatorRenderer.submit(mesh, matrices, pose, collector, facing, variant, orange, phase);
  }
}

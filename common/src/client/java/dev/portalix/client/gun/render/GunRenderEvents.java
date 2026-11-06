package dev.portalix.client.gun.render;

import dev.portalix.client.PortalixClient;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.render.ParticleView;
import dev.portalix.gun.GunPermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class GunRenderEvents {
  private GunRenderEvents() {}

  public static void register() {
    ClientPlatform.INSTANCE.registerRenderEvents();
  }

  public static void collect(SubmitNodeCollector collector, LevelRenderState state) {
    var camera = state.cameraRenderState;
    var view = new ParticleView(
        camera.pos,
        new Vec3(camera.orientation.transform(new Vector3f(1, 0, 0))),
        new Vec3(camera.orientation.transform(new Vector3f(0, 1, 0))),
        camera.initialized ? camera.cullFrustum::isVisible : bounds -> true);
    PortalixClient.portals().submit(view, collector.order(-1));
    PortalixClient.shotEffects().submit(view, collector.order(0));
  }

  public static boolean hidesBlockOutline(BlockPos pos) {
    return PortalixClient.portals().hidesBlockOutline(pos);
  }

  public static void renderCrosshair(GuiGraphicsExtractor graphics) {
    Minecraft client = Minecraft.getInstance();
    if (GunPermissions.canUse(client.player) && client.options.getCameraType().isFirstPerson()) {
      PortalixHud.render(graphics);
    }
  }
}

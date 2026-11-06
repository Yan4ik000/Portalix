package dev.portalix.client;

import dev.portalix.PortalixMod;
import dev.portalix.client.gun.model.GunMaterialTypes;
import dev.portalix.client.gun.model.GunModels;
import dev.portalix.client.particle.CarryParticleRenderer;
import dev.portalix.client.particle.GunIndicatorRenderer;
import dev.portalix.client.particle.SourceParticleSystem;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.render.ParticleRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

final class ModResourceReload extends SimplePreparableReloadListener<Void> {
  private ModResourceReload() {}

  static void register() {
    ClientPlatform.INSTANCE.registerReloadListener(PortalixMod.id("resources"), new ModResourceReload());
  }

  static void load() {
    ParticleRenderTypes.clear();
    GunMaterialTypes.clear();
    GunModels.load();
    CarryParticleRenderer.loadResources();
    GunIndicatorRenderer.loadResources();
    var texture = PortalixMod.id("textures/effects/particle_glow_05.png");
    ParticleRenderTypes.spritecard(texture, 1, false);
    for (int overbright : new int[] {1, 3, 15})
      ParticleRenderTypes.tintedParticle(texture, overbright, false);
  }

  @Override
  protected Void prepare(ResourceManager resources, ProfilerFiller profiler) {
    SourceParticleSystem.loadResources();
    return null;
  }

  @Override
  protected void apply(Void prepared, ResourceManager resources, ProfilerFiller profiler) {
    PortalSceneManager.clear();
    load();
    Minecraft client = Minecraft.getInstance();
    for (var id :
        client
            .getResourceManager()
            .listResources(
                "textures",
                id -> id.getNamespace().equals("portalix") && id.getPath().endsWith(".png"))
            .keySet()) client.getTextureManager().getTexture(id);
    GunModels.view().warmup();
    GunModels.world().warmup();
  }
}

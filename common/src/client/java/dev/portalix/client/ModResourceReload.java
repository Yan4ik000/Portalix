package dev.portalix.client;

import dev.portalix.PortalixMod;
import dev.portalix.client.gun.model.GunMaterialTypes;
import dev.portalix.client.gun.model.GunModels;
import dev.portalix.client.particle.CarryParticleRenderer;
import dev.portalix.client.particle.GunIndicatorRenderer;
import dev.portalix.client.particle.GunShotEffects;
import dev.portalix.client.particle.SourceParticleSystem;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.render.ParticleRenderTypes;
import dev.portalix.resource.ModAssetLoader;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class ModResourceReload extends SimplePreparableReloadListener<Void> {
  private static boolean bundledPortalShaders;
  private static boolean bundledPortalDepthShaders;
  private static boolean bundledParticleShaders;

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
    GunShotEffects.loadResources();
  }

  public static boolean bundledPortalShaders() {
    return bundledPortalShaders;
  }

  public static boolean bundledPortalDepthShaders() {
    return bundledPortalDepthShaders;
  }

  public static boolean bundledParticleShaders() {
    return bundledParticleShaders;
  }

  private static boolean vanillaShaders(ResourceManager resources, String... paths) {
    var vanilla = Minecraft.getInstance().getVanillaPackResources();
    for (String path : paths) {
      var resource = resources.getResource(Identifier.withDefaultNamespace(path)).orElse(null);
      if (resource == null || resource.source() != vanilla) return false;
    }
    return true;
  }

  private static boolean bundledShaders(ResourceManager resources, String... paths) {
    for (String path : paths) {
      var resource = resources.getResource(PortalixMod.id(path)).orElse(null);
      if (resource == null) return false;
      try (var active = resource.open();
          var bundled = ModAssetLoader.open("/assets/portalix/" + path)) {
        if (!Arrays.equals(active.readAllBytes(), bundled.readAllBytes())) return false;
      } catch (IOException _) {
        return false;
      }
    }
    return true;
  }

  private static void checkShaders(ResourceManager resources) {
    // The bounded render passes depend on the bundled shaders' sampling and transforms.
    boolean sharedShaders = vanillaShaders(resources,
        "shaders/include/projection.glsl", "shaders/include/dynamictransforms.glsl")
        && bundledShaders(resources, "shaders/include/color_space.glsl");
    boolean vanillaScreenQuad = vanillaShaders(resources, "shaders/core/screenquad.vsh");
    bundledParticleShaders = sharedShaders
        && vanillaScreenQuad
        && bundledShaders(resources, "shaders/core/source_particle.vsh",
            "shaders/core/source_particle.fsh", "shaders/core/shot_light_composite.fsh");
    bundledPortalShaders = sharedShaders
        && bundledShaders(resources, "shaders/core/portal.fsh", "shaders/core/portal.vsh",
            "shaders/include/source_portal.glsl");
    bundledPortalDepthShaders = bundledPortalShaders && vanillaScreenQuad
        && bundledShaders(resources, "shaders/core/portal_restore_depth.fsh");
  }

  @Override
  protected Void prepare(ResourceManager resources, ProfilerFiller profiler) {
    SourceParticleSystem.loadResources();
    return null;
  }

  @Override
  protected void apply(Void prepared, ResourceManager resources, ProfilerFiller profiler) {
    PortalSceneManager.clear();
    checkShaders(resources);
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

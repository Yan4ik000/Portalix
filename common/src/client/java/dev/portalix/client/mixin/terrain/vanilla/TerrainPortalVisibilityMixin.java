package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.portal.PortalLink;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
abstract class TerrainPortalVisibilityMixin {
  @Unique private static final float PORTALIX_ORIENTATION_COS = (float) Math.cos(Math.toRadians(1));
  @Shadow @Final private LevelRenderer levelRenderer;
  @Shadow @Final private LevelRenderState levelRenderState;
  @Unique private List<PortalLink> portalix$cameraPath = List.of();
  @Unique private Quaternionf portalix$visibleOrientation;

  @WrapOperation(
      method = "extract",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"))
  private boolean portalix$refreshVisibility(
      SectionOcclusionGraph graph, Operation<Boolean> original) {
    boolean changed = original.call(graph);
    var path = PortalSceneManager.cameraPath();
    boolean frameChanged = !path.equals(portalix$cameraPath);
    portalix$cameraPath = path;
    return changed
        || frameChanged
        || portalix$visibleOrientation == null
        || Math.abs(portalix$visibleOrientation.dot(levelRenderState.cameraRenderState.orientation))
            < PORTALIX_ORIENTATION_COS
        || !((PortalTerrainGraphAccess) graph)
            .portalix$ready(levelRenderState.cameraRenderState.pos);
  }

  @Inject(method = "applyFrustum", at = @At("HEAD"), cancellable = true)
  private void portalix$initialVisibility(Frustum frustum, CallbackInfo ci) {
    var graph = (PortalTerrainGraphAccess) levelRenderer.sectionOcclusionGraph();
    var camera = levelRenderState.cameraRenderState;
    if (portalix$visibleOrientation == null)
      portalix$visibleOrientation = new Quaternionf(camera.orientation);
    else portalix$visibleOrientation.set(camera.orientation);
    if (graph.portalix$ready(camera.pos)) return;
    levelRenderer.clearVisibleSections();
    graph.portalix$initialVisibility(
        camera, frustum, levelRenderer.visibleSections(), levelRenderer.nearbyVisibleSections());
    ci.cancel();
  }
}

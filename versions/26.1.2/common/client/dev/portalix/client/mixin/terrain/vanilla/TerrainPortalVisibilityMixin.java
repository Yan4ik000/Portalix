package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.minecraft.SceneExtractorAccess;
import dev.portalix.client.compat.minecraft.VanillaSceneTerrain;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.client.portal.terrain.PortalTerrainViewArea;
import dev.portalix.portal.PortalLink;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class TerrainPortalVisibilityMixin {
  @Unique private static final float PORTALIX_ORIENTATION_COS = (float) Math.cos(Math.toRadians(1));
  @Unique private List<PortalLink> portalix$cameraPath = List.of();
  @Unique private Quaternionf portalix$visibleOrientation;
  @Unique private int portalix$visibleRadius;
  @Unique private float portalix$previousRoll;

  @ModifyVariable(method = "getSectionStatistics", at = @At("STORE"), ordinal = 0)
  private int portalix$sectionStatisticsTotal(int total) {
    return portalix$viewSectionCount(total);
  }

  @ModifyReturnValue(method = "getTotalSections", at = @At("RETURN"))
  private double portalix$totalSections(double total) {
    return portalix$viewSectionCount((int) total);
  }

  @Unique
  private int portalix$viewSectionCount(int total) {
    return ((LevelRendererAccessor) this).portalix$viewArea() instanceof PortalTerrainViewArea view
        ? view.sectionCount()
        : total;
  }

  @ModifyExpressionValue(method = {
      "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
      "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/culling/Frustum;)V"
  }, at = @At(value = "FIELD", target =
      "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"), allow = 1)
  private ObjectArrayList<RenderSection> portalix$blockEntityWindow(ObjectArrayList<RenderSection> sections) {
    return VanillaSceneTerrain.renderSections(sections);
  }

  @Inject(method = "cullTerrain", at = @At("HEAD"))
  private void portalix$rollFrustum(Camera camera, Frustum frustum, boolean spectator, CallbackInfo ci) {
    Quaternionf upright = new Quaternionf().rotationYXZ(
        (float) Math.PI - (float) Math.toRadians(camera.yRot()), -(float) Math.toRadians(camera.xRot()), 0);
    Vector3f up = camera.rotation().transform(new Vector3f(0, 1, 0));
    Vector3f reference = upright.transform(new Vector3f(0, 1, 0));
    float roll = (float) Math.atan2(new Vector3f(reference).cross(up).dot(camera.forwardVector()), reference.dot(up));
    if (Math.abs(roll - portalix$previousRoll) > 1.0e-5F)
      ((SceneExtractorAccess) this).portalix$invalidateView();
    portalix$previousRoll = roll;
  }

  @WrapOperation(method = "cullTerrain", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"))
  private boolean portalix$refreshVisibility(SectionOcclusionGraph graph, Operation<Boolean> original) {
    var renderer = (LevelRendererAccessor) this;
    var camera = renderer.portalix$state().cameraRenderState;
    boolean changed = original.call(graph);
    boolean invalid = ((SceneExtractorAccess) this).portalix$consumeViewInvalidation();
    var path = PortalSceneManager.cameraPath();
    boolean pathChanged = !path.equals(portalix$cameraPath);
    portalix$cameraPath = path;
    int radius = renderer.portalix$viewArea().getViewDistance();
    boolean resized = radius != portalix$visibleRadius;
    portalix$visibleRadius = radius;
    return changed || invalid || resized || pathChanged || portalix$visibleOrientation == null
        || Math.abs(portalix$visibleOrientation.dot(camera.orientation)) < PORTALIX_ORIENTATION_COS
        || !((PortalTerrainGraphAccess) graph).portalix$ready(camera.pos);
  }

  @Inject(method = "applyFrustum", at = @At("HEAD"), cancellable = true)
  private void portalix$initialVisibility(Frustum frustum, CallbackInfo ci) {
    var renderer = (LevelRenderer) (Object) this;
    var access = (LevelRendererAccessor) this;
    var graph = (PortalTerrainGraphAccess) renderer.getSectionOcclusionGraph();
    var camera = access.portalix$state().cameraRenderState;
    if (portalix$visibleOrientation == null) portalix$visibleOrientation = new Quaternionf(camera.orientation);
    else portalix$visibleOrientation.set(camera.orientation);
    if (graph.portalix$ready(camera.pos)) return;
    renderer.getVisibleSections().clear();
    access.portalix$nearbySections().clear();
    graph.portalix$initialVisibility(camera, frustum, renderer.getVisibleSections(), access.portalix$nearbySections());
    ci.cancel();
  }
}

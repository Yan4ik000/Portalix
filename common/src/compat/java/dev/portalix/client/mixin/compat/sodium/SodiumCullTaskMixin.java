package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.client.compat.sodium.SodiumCullRequest;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import net.caffeinemc.mods.sodium.client.render.chunk.async.CullResult;
import net.caffeinemc.mods.sodium.client.render.chunk.async.CullTask;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = CullTask.class, remap = false)
abstract class SodiumCullTaskMixin implements SodiumCullRequest.SnapshotAccess {
  @Unique private PortalCullPlane portalix$exit;
  @Unique private SodiumCullRequest.Snapshot portalix$snapshot;

  @Inject(method = "<init>", at = @At("TAIL"))
  private void portalix$snapshotExit(
      Viewport viewport,
      float regularDistance,
      float localDistance,
      int frame,
      OcclusionCuller culler,
      boolean culling,
      Level level,
      CallbackInfo ci) {
    portalix$exit = SodiumCullRequest.plane(viewport);
    portalix$snapshot = new SodiumCullRequest.Snapshot(
        SodiumCullRequest.Footprint.of(viewport, Math.max(regularDistance, localDistance)));
  }

  @Override
  public SodiumCullRequest.Snapshot portalix$cullSnapshot() {
    return portalix$snapshot;
  }

  @WrapMethod(method = "runTask")
  private CullResult portalix$clippedVisibility(Operation<CullResult> original) {
    return ScopedValue.where(SodiumCullRequest.Snapshot.ACTIVE, portalix$snapshot)
        .call(() -> PortalOcclusionContext.call(portalix$exit, original::call));
  }
}

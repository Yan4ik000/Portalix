package dev.portalix.client.portal.terrain;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public interface PortalTerrainGraphAccess {
  void portalix$viewArea(ViewArea area);

  LongSet portalix$loadedChunks();

  LongSet portalix$emptySections();

  PortalOcclusionContext.Snapshot portalix$visibility();

  boolean portalix$ready(Vec3 camera);

  boolean portalix$settled(Vec3 camera);

  boolean portalix$occludes(CameraRenderState camera, AABB bounds);

  void portalix$initialVisibility(
      CameraRenderState camera,
      Frustum frustum,
      List<RenderSection> visible,
      List<RenderSection> nearby);
}

package dev.portalix.client.mixin.scene;

import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.compat.minecraft.SceneExtractorAccess;
import dev.portalix.client.portal.terrain.PortalRenderSections;
import dev.portalix.client.portal.terrain.SectionUpdateStorageAccess;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelExtractor.class)
public abstract class PortalExtractorStateMixin implements SceneExtractorAccess {
  @Shadow private SectionUpdateTracker sectionUpdateTracker;
  @Unique private boolean portalix$viewInvalid;
  @Shadow private int lastViewDistance;
  @Shadow private boolean shouldInvalidateCompiledGeometry;
  @Shadow private boolean shouldResetLevelRenderData;

  @Override
  public void portalix$shareTerrain(LevelExtractor primary) {
    var owner = (PortalExtractorStateMixin) (Object) primary;
    boolean terrainChanged = sectionUpdateTracker != owner.sectionUpdateTracker;
    sectionUpdateTracker = owner.sectionUpdateTracker;
    lastViewDistance = owner.lastViewDistance;
    shouldInvalidateCompiledGeometry = false;
    shouldResetLevelRenderData = false;
    if (PortalSceneManager.consumeTerrainPreparationChange() || terrainChanged) portalix$viewInvalid = true;
  }

  @Override
  public void portalix$invalidateView() {
    portalix$viewInvalid = true;
  }

  @Override
  public boolean portalix$consumeViewInvalidation() {
    boolean invalid = portalix$viewInvalid;
    portalix$viewInvalid = false;
    return invalid;
  }

  @Override
  public boolean portalix$dirtyTerrain(long section) {
    if (sectionUpdateTracker == null) return true;
    var storage = (PortalRenderSections<SectionUpdateTracker.SectionDirtyState>)
        ((SectionUpdateStorageAccess) sectionUpdateTracker).portalix$storage();
    var state = storage.getExisting(section);
    return state == null || state.isDirty();
  }

  @Override
  public void portalix$unloadColumns(LongSet columns) {
    if (sectionUpdateTracker == null) return;
    var storage =
        (PortalRenderSections<?>)
            ((SectionUpdateStorageAccess) sectionUpdateTracker).portalix$storage();
    for (long column : columns)
      storage.removeColumn(ChunkPos.getX(column), ChunkPos.getZ(column), value -> {});
  }
}

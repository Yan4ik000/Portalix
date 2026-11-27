package dev.portalix.client.mixin.scene;

import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
  @Accessor("levelRenderState")
  LevelRenderState portalix$state();

  @Mutable
  @Accessor("levelRenderState")
  void portalix$state(LevelRenderState state);

  @Accessor("level")
  void portalix$level(ClientLevel level);

  @Accessor("lastViewDistance")
  void portalix$viewDistance(int distance);

  @Accessor("ticks")
  int portalix$ticks();

  @Accessor("ticks")
  void portalix$ticks(int ticks);

  @Accessor("skyRenderer")
  void portalix$sky(SkyRenderer renderer);

  @Accessor("submitNodeStorage")
  SubmitNodeStorage portalix$submitNodeStorage();

  @Accessor("featureRenderDispatcher")
  FeatureRenderDispatcher portalix$featureDispatcher();

  @Accessor("destructionProgress")
  Long2ObjectMap<SortedSet<BlockDestructionProgress>> portalix$destructionProgress();

  @Mutable
  @Accessor("destructionProgress")
  void portalix$destructionProgress(Long2ObjectMap<SortedSet<BlockDestructionProgress>> progress);

  @Invoker("cullTerrain")
  void portalix$cullTerrain(Camera camera, Frustum frustum, boolean spectator);

  @Accessor("viewArea")
  ViewArea portalix$viewArea();

  @Accessor("viewArea")
  void portalix$viewArea(ViewArea area);

  @Mutable
  @Accessor("sectionOcclusionGraph")
  void portalix$graph(SectionOcclusionGraph graph);

  @Mutable
  @Accessor("visibleSections")
  void portalix$visibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections);

  @Accessor("nearbyVisibleSections")
  ObjectArrayList<SectionRenderDispatcher.RenderSection> portalix$nearbySections();

  @Mutable
  @Accessor("nearbyVisibleSections")
  void portalix$nearbySections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections);

  @Accessor("lastTranslucentSortBlockPos")
  BlockPos portalix$sortPosition();

  @Accessor("lastTranslucentSortBlockPos")
  void portalix$sortPosition(BlockPos position);

  @Accessor("translucencyResortIterationIndex")
  int portalix$sortIteration();

  @Accessor("translucencyResortIterationIndex")
  void portalix$sortIteration(int iteration);

  @Accessor("sectionRenderDispatcher")
  void portalix$sections(SectionRenderDispatcher dispatcher);

  @Accessor("entityOutlineTarget")
  RenderTarget portalix$outlineTarget();

  @Accessor("entityOutlineTarget")
  void portalix$outlineTarget(RenderTarget target);
}

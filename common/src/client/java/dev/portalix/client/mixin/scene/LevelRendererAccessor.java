package dev.portalix.client.mixin.scene;

import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
  @Mutable
  @Accessor("levelRenderState")
  void portalix$state(LevelRenderState state);

  @Invoker("repositionCamera")
  void portalix$reposition(CameraRenderState camera);

  @Invoker("compileSections")
  void portalix$compile(CameraRenderState camera);

  @Accessor("viewArea")
  void portalix$viewArea(ViewArea area);

  @Mutable
  @Accessor("sectionOcclusionGraph")
  void portalix$graph(SectionOcclusionGraph graph);

  @Mutable
  @Accessor("visibleSections")
  void portalix$visibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections);

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

  @Mutable
  @Accessor("entityOutlineTarget")
  void portalix$outlineTarget(RenderTarget target);
}

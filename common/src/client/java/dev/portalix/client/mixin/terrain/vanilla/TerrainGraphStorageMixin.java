package dev.portalix.client.mixin.terrain.vanilla;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.SectionOcclusionGraph$SectionToNodeMap")
abstract class TerrainGraphStorageMixin {
  // Stable section indices can be far apart after unloads; allocate only occupied pages.
  @Unique private static final int PORTALIX_PAGE_BITS = 10;
  @Unique private static final int PORTALIX_PAGE_SIZE = 1 << PORTALIX_PAGE_BITS;

  @Unique
  private final Int2ObjectOpenHashMap<SectionOcclusionGraph.Node[]> portalix$pages =
      new Int2ObjectOpenHashMap<>();

  @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
  private static int portalix$sparse(int size) {
    return 0;
  }

  @Inject(method = "put", at = @At("HEAD"), cancellable = true)
  private void portalix$put(
      SectionRenderDispatcher.RenderSection section,
      SectionOcclusionGraph.Node node,
      CallbackInfo ci) {
    int page = section.index >>> PORTALIX_PAGE_BITS;
    var nodes = portalix$pages.get(page);
    if (nodes == null) {
      nodes = new SectionOcclusionGraph.Node[PORTALIX_PAGE_SIZE];
      portalix$pages.put(page, nodes);
    }
    nodes[section.index & (PORTALIX_PAGE_SIZE - 1)] = node;
    ci.cancel();
  }

  @Inject(method = "get", at = @At("HEAD"), cancellable = true)
  private void portalix$get(
      SectionRenderDispatcher.RenderSection section,
      CallbackInfoReturnable<SectionOcclusionGraph.Node> cir) {
    var nodes = portalix$pages.get(section.index >>> PORTALIX_PAGE_BITS);
    var node = nodes == null ? null : nodes[section.index & (PORTALIX_PAGE_SIZE - 1)];
    cir.setReturnValue(node != null && node.section == section ? node : null);
  }
}

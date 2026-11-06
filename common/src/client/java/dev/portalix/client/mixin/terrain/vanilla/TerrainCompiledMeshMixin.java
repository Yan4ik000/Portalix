package dev.portalix.client.mixin.terrain.vanilla;

import dev.portalix.client.portal.terrain.SectionMeshBuffersAccess;
import dev.portalix.client.portal.terrain.SectionMeshTaskAccess;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSectionBufferSlice;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
abstract class TerrainCompiledMeshMixin implements SectionMeshTaskAccess, SectionMeshBuffersAccess {
  @Unique private static final int PORTALIX_LAYERS = ChunkSectionLayer.values().length;
  @Unique private AtomicBoolean portalix$cancelled;

  @Unique
  private final RenderSectionBufferSlice[] portalix$slices =
      new RenderSectionBufferSlice[PORTALIX_LAYERS];

  @Unique private BlockPos portalix$origin;

  @Override
  public RenderSectionBufferSlice portalix$slice(ChunkSectionLayer layer) {
    return portalix$slices[layer.ordinal()];
  }

  @Override
  public void portalix$slice(ChunkSectionLayer layer, RenderSectionBufferSlice slice) {
    portalix$slices[layer.ordinal()] = slice;
  }

  @Override
  public BlockPos portalix$origin() {
    return portalix$origin;
  }

  @Override
  public void portalix$origin(BlockPos origin) {
    portalix$origin = origin;
  }

  @Inject(method = "close", at = @At("HEAD"))
  private void portalix$releaseBuffers(CallbackInfo ci) {
    PortalSceneManager.terrainMeshReleased((CompiledSectionMesh) (Object) this);
    Arrays.fill(portalix$slices, null);
    portalix$origin = null;
  }

  @Override
  public void portalix$task(AtomicBoolean cancelled) {
    portalix$cancelled = cancelled;
  }

  @Override
  public boolean portalix$cancelled() {
    return portalix$cancelled != null && portalix$cancelled.get();
  }
}

package dev.portalix.client.mixin.terrain.vanilla;

import dev.portalix.client.portal.terrain.SectionMeshBuffersAccess;
import dev.portalix.client.portal.terrain.SectionMeshTaskAccess;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSectionBufferSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
abstract class TerrainCompiledMeshMixin implements SectionMeshTaskAccess, SectionMeshBuffersAccess {
  @Unique private static final ChunkSectionLayer[] PORTALIX_LAYERS = ChunkSectionLayer.values();
  @Unique private static final int PORTALIX_MASK_BITS = Math.min(Byte.SIZE, PORTALIX_LAYERS.length);
  @Unique private static final ChunkSectionLayer[][] PORTALIX_LAYER_SETS = portalix$layerSets();
  @Unique private int portalix$layerMask;
  @Unique private AtomicBoolean portalix$cancelled;

  @Unique
  private final RenderSectionBufferSlice[] portalix$slices =
      new RenderSectionBufferSlice[PORTALIX_LAYERS.length];

  @Unique
  private static ChunkSectionLayer[][] portalix$layerSets() {
    var sets = new ChunkSectionLayer[1 << PORTALIX_MASK_BITS][];
    // Leave additional modded layers in native order without growing the table exponentially.
    int extra = PORTALIX_LAYERS.length - PORTALIX_MASK_BITS;
    for (int mask = 0; mask < sets.length; mask++) {
      var layers = new ChunkSectionLayer[Integer.bitCount(mask) + extra];
      int index = 0;
      for (int i = 0; i < PORTALIX_MASK_BITS; i++)
        if ((mask & (1 << i)) != 0) layers[index++] = PORTALIX_LAYERS[i];
      System.arraycopy(PORTALIX_LAYERS, PORTALIX_MASK_BITS, layers, index, extra);
      sets[mask] = layers;
    }
    return sets;
  }

  @Override
  public ChunkSectionLayer[] portalix$allocatedLayers() {
    return PORTALIX_LAYER_SETS[portalix$layerMask];
  }

  @Override
  public RenderSectionBufferSlice portalix$slice(ChunkSectionLayer layer) {
    return portalix$slices[layer.ordinal()];
  }

  @Override
  public void portalix$slice(ChunkSectionLayer layer, RenderSectionBufferSlice slice) {
    int ordinal = layer.ordinal();
    portalix$slices[ordinal] = slice;
    if (ordinal < PORTALIX_MASK_BITS) {
      if (slice == null) portalix$layerMask &= ~(1 << ordinal);
      else portalix$layerMask |= 1 << ordinal;
    }
  }

  @Inject(method = "close", at = @At("HEAD"))
  private void portalix$releaseBuffers(CallbackInfo ci) {
    PortalSceneManager.terrainMeshReleased((CompiledSectionMesh) (Object) this);
    Arrays.fill(portalix$slices, null);
    portalix$layerMask = 0;
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

package dev.portalix.client.compat.sodium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class SodiumDrawListsTest {
  private static final Viewport EXIT = viewport(64);
  private static final Viewport LEFT_EYE = viewport(127);
  private static final Viewport SAME_SECTION = viewport(126);
  private static final Viewport RIGHT_EYE = viewport(129);

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void eyeSectionInvalidatesDrawListsWithStableVisibility(boolean fallback) {
    float distance = 128;
    // Sodium selects the multi-forest above 32 chunks without consulting world height.
    var visibility = new SectionTree(EXIT, 544, 1, CullType.REGULAR, null);
    assertTrue(visibility.isValidFor(EXIT, distance));
    var source = fallback ? null : visibility;
    var state = new SodiumViewCullingState();
    var lists = SortedRenderLists.empty();
    var footprint = SodiumCullRequest.Footprint.of(EXIT, distance);
    state.rememberLists(null, 42, LEFT_EYE.getChunkCoord(), distance, source, visibility, lists, null, footprint);

    var retained = state.cachedLists(null, 42, SAME_SECTION.getChunkCoord(), distance, source);
    assertNotNull(retained);
    assertSame(lists, retained.lists());
    assertNull(state.cachedLists(null, 42, RIGHT_EYE.getChunkCoord(), distance, source));
  }

  @Test
  void eyeSectionChangesTranslucentRegionOrderAndInvalidatesBatches() {
    var left = new CountingRegion(0);
    var right = new CountingRegion(1);
    var collector = new VisibleChunkCollector(null, 1);
    collector.getUnsortedRenderLists().addAll(List.of(left.getRenderList(), right.getRenderList()));

    assertSame(right, collector.createRenderLists(LEFT_EYE).iterator(true).next().getRegion());
    int leftBefore = left.invalidations;
    int rightBefore = right.invalidations;
    assertSame(right, collector.createRenderLists(SAME_SECTION).iterator(true).next().getRegion());
    assertEquals(leftBefore, left.invalidations);
    assertEquals(rightBefore, right.invalidations);

    assertSame(left, collector.createRenderLists(RIGHT_EYE).iterator(true).next().getRegion());
    assertEquals(leftBefore + 1, left.invalidations);
    assertEquals(rightBefore + 1, right.invalidations);
  }

  private static Viewport viewport(int x) {
    return new Viewport(null, new Vector3d(x, 72, 0));
  }

  private static final class CountingRegion extends RenderRegion {
    private int invalidations;

    CountingRegion(int x) {
      super(x, 1, 0, null);
    }

    @Override
    public void clearAllCachedBatches() {
      invalidations++;
    }
  }
}

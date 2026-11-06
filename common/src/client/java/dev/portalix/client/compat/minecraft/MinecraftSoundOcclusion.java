package dev.portalix.client.compat.minecraft;

import dev.portalix.client.world.RetainedChunkAccess;
import dev.portalix.portal.audio.PortalSoundPropagation;
import dev.portalix.resource.SourceUnits;
import dev.portalix.sound.SourceSoundAttenuation;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectiond;
import org.joml.Vector2d;
import org.jspecify.annotations.Nullable;

public final class MinecraftSoundOcclusion implements PortalSoundPropagation.Transmission {
  private final ClientLevel level;
  private final @Nullable Entity listener;
  private @Nullable List<LevelChunk> retainedChunks;

  private MinecraftSoundOcclusion(ClientLevel level, @Nullable Entity listener) {
    this.level = level;
    this.listener = listener;
  }

  static PortalSoundPropagation.Transmission prepare() {
    var client = Minecraft.getInstance();
    return client.level == null ? (start, end, radius) -> 1
        : new MinecraftSoundOcclusion(client.level, client.player);
  }

  public static double transmission(Vec3 start, Vec3 end, double sourceRadius) {
    return prepare().gain(start, end, sourceRadius);
  }

  @Override
  public double gain(Vec3 start, Vec3 end, double sourceRadius) {
    var emitter = BlockPos.containing(end);
    double steps = Math.abs(end.x - start.x) + Math.abs(end.y - start.y)
        + Math.abs(end.z - start.z) + 2 * sourceRadius;
    var source = level.getChunkSource();
    List<LevelChunk> chunks = null;
    if (steps > source.getLoadedChunksCount()) {
      if (retainedChunks == null)
        retainedChunks = ((RetainedChunkAccess) source).portalix$retainedChunks();
      chunks = retainedChunks;
    }
    if (clear(start, end, emitter, chunks)) return 1;
    if (sourceRadius <= 0) return SourceSoundAttenuation.occlusionGain(4);
    var direction = start.subtract(end).normalize();
    double horizontal = Math.hypot(direction.x, direction.z);
    var right = horizontal == 0 ? new Vec3(0, 0, 1)
        : new Vec3(-direction.z / horizontal, 0, direction.x / horizontal);
    var up = right.cross(direction);
    var first = right.add(up).normalize();
    // Sample below high emitters too, rather than placing both diagonals above their cover.
    if (end.y > start.y + 120 * SourceUnits.SOURCE_UNIT_BLOCKS) up = new Vec3(up.x, -up.y, up.z);
    var second = up.subtract(right).normalize();
    int obscured = 0;
    for (int band = 1; band <= 2; band++) {
      double radius = sourceRadius * band / 2;
      if (!clear(start, end.add(first.scale(radius)), emitter, chunks)) obscured++;
      if (!clear(start, end.add(second.scale(radius)), emitter, chunks)) obscured++;
    }
    return SourceSoundAttenuation.occlusionGain(obscured);
  }

  private boolean clear(Vec3 start, Vec3 end, BlockPos emitter,
      @Nullable List<LevelChunk> chunks) {
    if (chunks == null) return clear(start, end, emitter);
    var delta = end.subtract(start);
    var interval = new Vector2d();
    // Unloaded columns are air. Clip only the loaded parts of arbitrarily long world rays.
    for (var chunk : chunks) {
      var pos = chunk.getPos();
      // Keep the broad phase conservative for rays exactly on a column boundary.
      double epsilon = 1e-7;
      if (!Intersectiond.intersectRayAab(start.x, start.y, start.z, delta.x, delta.y, delta.z,
          pos.getMinBlockX() - epsilon, level.getMinY() - epsilon,
          pos.getMinBlockZ() - epsilon, pos.getMaxBlockX() + 1 + epsilon,
          level.getMaxY() + 1 + epsilon, pos.getMaxBlockZ() + 1 + epsilon, interval)) continue;
      double first = Math.max(0, interval.x);
      double last = Math.min(1, interval.y);
      if (first <= last
          && !clear(start.lerp(end, first), start.lerp(end, last), emitter)) return false;
    }
    return true;
  }

  private boolean clear(Vec3 start, Vec3 end, BlockPos emitter) {
    var context = new ClipContext(start, end, ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE, listener);
    return BlockGetter.traverseBlocks(start, end, context, (ray, pos) -> {
      // A sound emitted inside a solid block must not be occluded by that block itself.
      if (pos.equals(emitter)) return null;
      var state = level.getBlockState(pos);
      var shape = ray.getBlockShape(state, level, pos);
      return level.clipWithInteractionOverride(start, end, pos, shape, state) == null ? null : false;
    }, ray -> true);
  }
}

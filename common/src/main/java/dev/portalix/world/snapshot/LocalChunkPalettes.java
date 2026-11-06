package dev.portalix.world.snapshot;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class LocalChunkPalettes {
  private static final ScopedValue<Boolean> SENDING = ScopedValue.newInstance();
  private static final ScopedValue<Reader> READING = ScopedValue.newInstance();
  private static final ScopedValue<List<PaletteSnapshot<BlockState>>> DECODING =
      ScopedValue.newInstance();

  private LocalChunkPalettes() {}

  public static void send(boolean local, Runnable action) {
    ScopedValue.where(SENDING, local).run(action);
  }

  public static boolean sending() {
    return SENDING.isBound() && SENDING.get();
  }

  public static void decode(ByteBuf input, Runnable action) {
    if (input instanceof LocalPaletteBuffer local)
      ScopedValue.where(DECODING, local.palettes()).run(action);
    else action.run();
  }

  public static @Nullable List<PaletteSnapshot<BlockState>> decoding() {
    return DECODING.isBound() ? DECODING.get() : null;
  }

  public static void read(ClientboundLevelChunkPacketData packet, Runnable action) {
    var snapshots = ((ChunkPaletteSnapshots) packet).portalix$palettes();
    if (snapshots == null) action.run();
    else ScopedValue.where(READING, new Reader(snapshots)).run(action);
  }

  public static @Nullable PaletteSnapshot<BlockState> next() {
    if (!READING.isBound()) return null;
    var reader = READING.get();
    return reader.index < reader.snapshots.size() ? reader.snapshots.get(reader.index++) : null;
  }

  private static final class Reader {
    final List<PaletteSnapshot<BlockState>> snapshots;
    int index;

    Reader(List<PaletteSnapshot<BlockState>> snapshots) {
      this.snapshots = snapshots;
    }
  }
}

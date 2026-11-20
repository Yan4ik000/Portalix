package dev.portalix.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class ChunkWatchTestHooks implements AutoCloseable {
  public enum Event { WATCH, SENT, UNWATCH }

  private static ChunkWatchTestHooks active;
  private final ServerPlayer player;
  private final ServerLevel level;
  private final ChunkPos pos;
  private final List<Event> events = new ArrayList<>();

  private ChunkWatchTestHooks(ServerPlayer player, ChunkPos pos) {
    this.player = player;
    this.level = player.level();
    this.pos = pos;
  }

  static ChunkWatchTestHooks trace(ServerPlayer player, ChunkPos pos) {
    if (active != null) throw new IllegalStateException("Another chunk watch trace is active");
    active = new ChunkWatchTestHooks(player, pos);
    return active;
  }

  public static void record(ServerPlayer player, ServerLevel level, ChunkPos pos, Event event) {
    if (active != null && active.player == player && active.level == level && active.pos.equals(pos))
      active.events.add(event);
  }

  List<Event> events() {
    return events;
  }

  public void close() {
    active = null;
  }
}

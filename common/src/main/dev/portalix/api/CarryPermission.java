package dev.portalix.api;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface CarryPermission {

  Event EVENT = new Event();

  boolean allow(ServerPlayer player, Entity entity, Operation operation, Vec3 destination);

  enum Operation {
    GRAB,
    MOVE,
    THROW
  }

  final class Event implements CarryPermission {
    private volatile CarryPermission[] listeners = new CarryPermission[0];

    private Event() {}

    public synchronized void register(CarryPermission listener) {
      Objects.requireNonNull(listener);
      var updated = Arrays.copyOf(listeners, listeners.length + 1);
      updated[listeners.length] = listener;
      listeners = updated;
    }

    public CarryPermission invoker() {
      return this;
    }

    @Override
    public boolean allow(ServerPlayer player, Entity entity, Operation operation, Vec3 destination) {
      for (var listener : listeners)
        if (!listener.allow(player, entity, operation, destination)) return false;
      return true;
    }
  }
}

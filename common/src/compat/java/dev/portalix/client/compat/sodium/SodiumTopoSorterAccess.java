package dev.portalix.client.compat.sodium;

public interface SodiumTopoSorterAccess {
  State portalix$state();

  void portalix$state(State state, boolean crossedPlane);

  record State(boolean planes, boolean distance, int failures) {}
}

package dev.portalix.world.chunk;

public interface WarmColumnAccess {
  void portalix$retainColumn(long column);

  void portalix$releaseColumn(long column);
}

package dev.portalix.world.snapshot;

public interface SkyLightColumnAccess {
  int portalix$getTop(long column);

  int portalix$putTop(long column, int top);

  int portalix$removeTop(long column);

  void portalix$defaultTop(int bottom);

  void portalix$copyColumnsFrom(SkyLightColumnAccess source);
}

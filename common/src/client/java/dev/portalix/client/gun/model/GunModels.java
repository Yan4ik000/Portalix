package dev.portalix.client.gun.model;

public final class GunModels {
  private GunModels() {}

  private static GunModel view;
  private static GunModel world;

  public static void load() {
    GunModel nextView = new GunModel("view");
    GunModel nextWorld;
    try {
      nextWorld = new GunModel("world");
    } catch (RuntimeException | Error failure) {
      nextView.close();
      throw failure;
    }
    if (view != null) view.close();
    if (world != null) world.close();
    view = nextView;
    world = nextWorld;
  }

  public static GunModel view() {
    return view;
  }

  public static GunModel world() {
    return world;
  }
}

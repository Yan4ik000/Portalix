package dev.portalix.test.client;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** Captures the image submitted to the window, including world, hand, postprocessing and GUI. */
public final class FrameCapture {
  static final int MAX_IN_FLIGHT = 3;
  private static final long ORIGIN = System.nanoTime();
  private static Request requested;
  private static int reading;
  private static long frameId;
  private static long renderFrame;
  private static CameraSnapshot worldCamera;
  private static Presentation presentation = new Presentation(0, 0, null);

  private FrameCapture() {}

  public static long monotonicNanos() {
    return System.nanoTime() - ORIGIN;
  }

  public static synchronized boolean canCapture() {
    return requested == null && reading < MAX_IN_FLIGHT;
  }

  public static synchronized CompletableFuture<Frame> capture(Minecraft client, String name, Path directory) {
    RenderSystem.assertOnRenderThread();
    if (client != Minecraft.getInstance()) throw new IllegalArgumentException("Different Minecraft instance");
    Request request = request(Objects.requireNonNull(name), Objects.requireNonNull(directory));
    requested = request;
    return request.result;
  }

  public static synchronized CompletableFuture<Frame> captureSample(Minecraft client) {
    RenderSystem.assertOnRenderThread();
    if (client != Minecraft.getInstance()) throw new IllegalArgumentException("Different Minecraft instance");
    Request request = request(null, null);
    requested = request;
    return request.result;
  }

  private static Request request(String name, Path directory) {
    if (name != null && !name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) throw new IllegalArgumentException("Invalid capture name: " + name);
    if (!canCapture() || name != null && reading != 0)
      throw new IllegalStateException("The frame capture queue has no capacity for this request");
    return new Request(name, directory, new CompletableFuture<>());
  }

  public static void beginFrame() {
    renderFrame++;
    worldCamera = null;
  }

  public static long renderFrame() { return renderFrame; }

  static CameraSnapshot worldCamera() { return worldCamera; }
  static Presentation presentation() { return presentation; }

  /** Captures the uploaded projection; Iris moves bobbing into the later model-view matrix. */
  public static void worldProjection(Matrix4fc projection, CameraRenderState camera) {
    worldCamera = cameraSnapshot(projection, camera);
  }

  public static void worldModelView(Matrix4fc modelView) {
    if (worldCamera != null) worldCamera.viewRotation.set(modelView);
  }

  /** Called exactly once immediately before Minecraft blits its completed image into the window. */
  public static synchronized void onFrame(RenderTarget target) {
    RenderSystem.assertOnRenderThread();
    frameId++;
    presentation = new Presentation(frameId, monotonicNanos(), worldCamera);
    if (requested == null) return;
    Request request = requested;
    requested = null;
    reading++;
    read(target, request, worldCamera, "main-window-before-blit");
  }

  private static CameraSnapshot cameraSnapshot(Matrix4fc projection, CameraRenderState camera) {
    return new CameraSnapshot(camera.pos, camera.xRot, camera.yRot,
        new Matrix4f(projection), new Matrix4f(camera.viewRotationMatrix),
        RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
  }

  private static void read(RenderTarget target, Request request, CameraSnapshot camera, String source) {
    Minecraft client = Minecraft.getInstance();
    long id = frameId;
    long tick = client.level == null ? -1 : client.level.getGameTime();
    int playerTick = client.player == null ? -1 : client.player.tickCount;
    int width = target.width;
    int height = target.height;
    long presentationNanos = monotonicNanos();
    try {
      Screenshot.takeScreenshot(target, image -> {
        // Do not throw from this callback: Screenshot owns a mapped GPU buffer around it.
        try {
          int[] pixels = image.getPixels();
          Frame frame = new Frame(id, presentationNanos, tick, playerTick, width, height, pixels, camera,
              request.directory == null ? null : request.directory.resolve(request.name + ".png"), source);
          if (request.directory == null) {
            image.close();
            finish(request, frame, null);
            return;
          }
          Util.ioPool().execute(() -> {
            try (image) {
              Files.createDirectories(request.directory);
              if (Files.exists(frame.png)) throw new IllegalStateException("Capture already exists: " + frame.png);
              image.writeToFile(frame.png);
              Files.writeString(request.directory.resolve(request.name + ".json"),
                  new GsonBuilder().setPrettyPrinting().create().toJson(frame.metadata()));
              finish(request, frame, null);
            } catch (Throwable failure) {
              finish(request, null, failure);
            }
          });
        } catch (Throwable failure) {
          image.close();
          finish(request, null, failure);
        }
      });
    } catch (Throwable failure) {
      finish(request, null, failure);
    }
  }

  private static synchronized void finish(Request request, Frame frame, Throwable failure) {
    reading--;
    if (failure == null) request.result.complete(frame);
    else request.result.completeExceptionally(failure);
  }

  private record Request(String name, Path directory, CompletableFuture<Frame> result) {}
  record CameraSnapshot(Vec3 position, float pitch, float yaw, Matrix4f projection,
                                Matrix4f viewRotation, boolean zeroToOne) {}
  record Presentation(long frameId, long nanos, CameraSnapshot camera) {}

  public record Point(double x, double y) {}

  /** Pixel coordinates have a top-left origin and an exclusive right and bottom edge. */
  public record Region(int x, int y, int width, int height) {
    public Region {
      if (width <= 0 || height <= 0) throw new IllegalArgumentException("Empty region");
    }
    public int area() { return Math.multiplyExact(width, height); }
  }

  public record Statistics(int samples, int matched, double fraction, double meanLuminance,
                           double luminanceDeviation, Optional<Point> centroid, Optional<Region> extent) {}

  public record Difference(double meanChannelDifference, double changedFraction, int changedPixels) {}

  /** Broad material signatures tolerate lighting, antialiasing and shader tone mapping. */
  public enum Material implements IntPredicate {
    RED, GREEN, BLUE, ORANGE, CYAN, MAGENTA, LIGHT, DARK;

    @Override public boolean test(int argb) {
      int r = red(argb), g = green(argb), b = blue(argb);
      return switch (this) {
        case RED -> r >= 35 && r > g * 1.35 && r > b * 1.35;
        case GREEN -> g >= 35 && g > r * 1.3 && g > b * 1.3;
        case BLUE -> b >= 35 && b > r * 1.3 && b > g * 1.15;
        case ORANGE -> r >= 50 && g >= 20 && r > g * 1.2 && g > b * 1.35;
        case CYAN -> g >= 40 && b >= 40 && Math.min(g, b) > r * 1.3;
        case MAGENTA -> r >= 40 && b >= 40 && Math.min(r, b) > g * 1.3;
        case LIGHT -> Math.min(r, Math.min(g, b)) >= 180;
        case DARK -> Math.max(r, Math.max(g, b)) <= 12;
      };
    }
  }

  public static final class Frame {
    private final long id;
    private final long presentationNanos;
    private final long gameTick;
    private final int playerTick;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final CameraSnapshot camera;
    private final Path png;
    private final String source;

    private Frame(long id, long presentationNanos, long gameTick, int playerTick, int width, int height, int[] pixels,
                  CameraSnapshot camera, Path png, String source) {
      this.id = id;
      this.presentationNanos = presentationNanos;
      this.gameTick = gameTick;
      this.playerTick = playerTick;
      this.width = width;
      this.height = height;
      this.pixels = pixels;
      this.camera = camera;
      this.png = png;
      this.source = source;
    }

    public long id() { return id; }
    public long presentationNanos() { return presentationNanos; }
    public long gameTick() { return gameTick; }
    public int playerTick() { return playerTick; }
    public int width() { return width; }
    public int height() { return height; }
    public Path png() { return png; }
    public Region whole() { return new Region(0, 0, width, height); }
    public Vec3 cameraPosition() { return requireCamera().position; }
    public float cameraPitch() { return requireCamera().pitch; }
    public float cameraYaw() { return requireCamera().yaw; }


    public BufferedImage toBufferedImage() {
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      image.setRGB(0, 0, width, height, pixels, 0, width);
      return image;
    }

    public int pixel(int x, int y) {
      if (x < 0 || y < 0 || x >= width || y >= height) throw new IndexOutOfBoundsException();
      return pixels[y * width + x];
    }

    /** Projection describes geometry, not visibility through terrain or other occluders. */
    public Optional<Point> project(Vec3 position) {
      return projectUnclipped(position).filter(point ->
          point.x >= 0 && point.x <= width && point.y >= 0 && point.y <= height);
    }

    /** Project a control hull onto the image plane; its vertices may lie outside the viewport. */
    Optional<Point> projectUnclipped(Vec3 position) {
      Vector4f clip = clip(position);
      CameraSnapshot view = requireCamera();
      if (clip.w <= 1e-5 || clip.z > clip.w || clip.z < (view.zeroToOne ? 0 : -clip.w)) return Optional.empty();
      return Optional.of(screen(clip));
    }

    /** Conservative viewport-clipped bounds; depth/front clipping handles boxes crossing the near plane. */
    public Optional<Region> project(AABB box) {
      Vector4f[] corners = new Vector4f[8];
      for (int i = 0; i < 8; i++) corners[i] = clip(new Vec3(
          (i & 1) == 0 ? box.minX : box.maxX,
          (i & 2) == 0 ? box.minY : box.maxY,
          (i & 4) == 0 ? box.minZ : box.maxZ));
      List<Point> points = new ArrayList<>();
      for (int i = 0; i < 8; i++) for (int axis = 1; axis <= 4; axis *= 2) {
        if ((i & axis) != 0) continue;
        Vector4f a = new Vector4f(corners[i]), b = new Vector4f(corners[i | axis]);
        if (clipDepth(a, b, requireCamera().zeroToOne)) {
          points.add(screen(a));
          points.add(screen(b));
        }
      }
      if (points.isEmpty()) return Optional.empty();
      double minX = width, minY = height, maxX = 0, maxY = 0;
      for (Point point : points) {
        minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
        maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
      }
      int x = (int) Math.max(0, Math.floor(minX)), y = (int) Math.max(0, Math.floor(minY));
      int right = (int) Math.min(width, Math.ceil(maxX)), bottom = (int) Math.min(height, Math.ceil(maxY));
      return right <= x || bottom <= y ? Optional.empty() : Optional.of(new Region(x, y, right - x, bottom - y));
    }

    private CameraSnapshot requireCamera() {
      if (camera == null) throw new IllegalStateException("No world was rendered in frame " + id);
      return camera;
    }

    private Vector4f clip(Vec3 point) {
      CameraSnapshot view = requireCamera();
      Vec3 local = point.subtract(view.position);
      Vector4f clip = new Vector4f((float) local.x, (float) local.y, (float) local.z, 1);
      view.viewRotation.transform(clip);
      view.projection.transform(clip);
      return clip;
    }

    private Point screen(Vector4f clip) {
      return new Point((clip.x / clip.w + 1) * width * .5, (1 - clip.y / clip.w) * height * .5);
    }

    public Statistics statistics(Region region, IntPredicate mask) {
      validate(region);
      int matched = 0, minX = width, minY = height, maxX = -1, maxY = -1;
      double luminance = 0, squares = 0, sumX = 0, sumY = 0;
      for (int y = region.y; y < region.y + region.height; y++) for (int x = region.x; x < region.x + region.width; x++) {
        int pixel = pixels[y * width + x];
        double value = .2126 * red(pixel) + .7152 * green(pixel) + .0722 * blue(pixel);
        luminance += value; squares += value * value;
        if (mask.test(pixel)) {
          matched++; sumX += x + .5; sumY += y + .5;
          minX = Math.min(minX, x); minY = Math.min(minY, y);
          maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
      }
      double mean = luminance / region.area();
      return new Statistics(region.area(), matched, (double) matched / region.area(), mean,
          Math.sqrt(Math.max(0, squares / region.area() - mean * mean)),
          matched == 0 ? Optional.empty() : Optional.of(new Point(sumX / matched, sumY / matched)),
          matched == 0 ? Optional.empty() : Optional.of(new Region(minX, minY, maxX - minX + 1, maxY - minY + 1)));
    }

    public Difference difference(Frame other, Region region, int channelThreshold) {
      validate(region);
      if (width != other.width || height != other.height) throw new IllegalArgumentException("Different frame sizes");
      if (channelThreshold < 0 || channelThreshold > 255) throw new IllegalArgumentException("Invalid pixel threshold");
      long total = 0;
      int changed = 0;
      for (int y = region.y; y < region.y + region.height; y++) for (int x = region.x; x < region.x + region.width; x++) {
        int first = pixels[y * width + x], second = other.pixels[y * width + x];
        int r = Math.abs(red(first) - red(second)), g = Math.abs(green(first) - green(second)), b = Math.abs(blue(first) - blue(second));
        total += r + g + b;
        if (Math.max(r, Math.max(g, b)) > channelThreshold) changed++;
      }
      return new Difference((double) total / (region.area() * 3L), (double) changed / region.area(), changed);
    }

    public void assertNotBlack() {
      assertNotBlack(whole());
    }

    public void assertNotBlack(Region region) {
      Statistics stats = statistics(region, pixel -> (pixel & 0xffffff) != 0);
      require(stats.fraction > .01 && stats.meanLuminance > 0, "Image region is black: " + region + ": " + stats);
    }

    public Statistics assertMaterial(Region region, IntPredicate material, double minimumFraction, double maximumFraction) {
      if (minimumFraction < 0 || maximumFraction > 1 || minimumFraction > maximumFraction) throw new IllegalArgumentException("Invalid fraction range");
      Statistics stats = statistics(region, material);
      require(stats.fraction >= minimumFraction && stats.fraction <= maximumFraction,
          "Material fraction outside [" + minimumFraction + ", " + maximumFraction + "] in " + region + ": " + stats);
      return stats;
    }

    /** Spatial evidence for a sprite or particle cloud in an authored region, rather than one bright pixel. */
    public Statistics assertSprite(Region region, IntPredicate material, int minimumPixels, int maximumPixels,
                                   int minimumSpanX, int minimumSpanY) {
      if (minimumPixels < 1 || maximumPixels < minimumPixels || minimumSpanX < 1 || minimumSpanY < 1)
        throw new IllegalArgumentException("Invalid sprite bounds");
      Statistics stats = statistics(region, material);
      require(stats.matched >= minimumPixels && stats.matched <= maximumPixels,
          "Sprite pixel count outside [" + minimumPixels + ", " + maximumPixels + "]: " + stats);
      Region extent = stats.extent.orElseThrow();
      require(extent.width >= minimumSpanX && extent.height >= minimumSpanY, "Sprite extent too small: " + extent);
      return stats;
    }

    private void validate(Region region) {
      if (region.x < 0 || region.y < 0 || (long) region.x + region.width > width || (long) region.y + region.height > height)
        throw new IllegalArgumentException("Region outside " + width + "x" + height + ": " + region);
    }

    private void require(boolean condition, String message) {
      if (!condition) throw new AssertionError(message + " [frame=" + id + ", tick=" + gameTick + ", png=" + png + "]");
    }

    public Map<String, Object> metadata() {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("source", source); result.put("frameId", id); result.put("gameTick", gameTick);
      result.put("presentationNanos", presentationNanos);
      result.put("playerTick", playerTick); result.put("width", width); result.put("height", height);
      if (camera != null) {
        result.put("cameraPosition", List.of(camera.position.x, camera.position.y, camera.position.z));
        result.put("pitch", camera.pitch); result.put("yaw", camera.yaw); result.put("zeroToOneDepth", camera.zeroToOne);
        result.put("worldProjectionColumnMajor", camera.projection.get(new float[16]));
        result.put("viewRotationColumnMajor", camera.viewRotation.get(new float[16]));
      }
      return result;
    }
  }

  private static boolean clipDepth(Vector4f a, Vector4f b, boolean zeroToOne) {
    for (int plane = 0; plane < 3; plane++) {
      float da = depthDistance(a, plane, zeroToOne), db = depthDistance(b, plane, zeroToOne);
      if (da < 0 && db < 0) return false;
      if (da < 0) a.lerp(b, da / (da - db));
      else if (db < 0) b.lerp(a, db / (db - da));
    }
    return true;
  }

  private static float depthDistance(Vector4f point, int plane, boolean zeroToOne) {
    return switch (plane) {
      case 0 -> point.w - 1e-5f;
      case 1 -> point.w - point.z;
      case 2 -> zeroToOne ? point.z : point.z + point.w;
      default -> throw new IllegalArgumentException();
    };
  }

  private static int red(int pixel) { return (pixel >>> 16) & 255; }
  private static int green(int pixel) { return (pixel >>> 8) & 255; }
  private static int blue(int pixel) { return pixel & 255; }
}

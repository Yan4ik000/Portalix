package dev.portalix.test.client;

import com.google.gson.GsonBuilder;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import org.joml.Vector4f;

/** A small GPU readback of every presented gameplay frame catches flashes between review samples. */
final class PresentedFrameGuard {
  private static final int WIDTH = 64;
  private static final int HEIGHT = 36;
  private static final int TERRAIN_SCALE = 4;

  private final Path directory;
  private RenderTarget thumbnail;
  private RenderTarget terrainThumbnail;
  private final ArrayDeque<GpuBuffer> readbacks = new ArrayDeque<>();
  private final ArrayDeque<GpuBuffer> terrainReadbacks = new ArrayDeque<>();
  private long presented;
  private long checked;
  private int pending;
  private boolean closing;
  private Throwable failure;
  private boolean failureReported;
  private int[] previousPixels;
  private FrameCapture.CameraSnapshot previousCamera;
  private long previousFrame;
  private final CompletableFuture<Void> completion = new CompletableFuture<>();

  PresentedFrameGuard(Path directory) { this.directory = directory; }

  void presented(RenderTarget source, Consumer<FrameCapture.Frame> checkTerrain) {
    RenderSystem.assertOnRenderThread();
    check();
    if (closing) { releaseWhenIdle(); return; }
    if (checkTerrain == null && thumbnail == null)
      thumbnail = new TextureTarget("Portalix frame thumbnail", WIDTH, HEIGHT, GpuFormat.RGBA8_UNORM, null);
    if (checkTerrain != null && terrainThumbnail == null)
      terrainThumbnail = new TextureTarget("Portalix terrain thumbnail", WIDTH * TERRAIN_SCALE, HEIGHT * TERRAIN_SCALE,
          GpuFormat.RGBA8_UNORM, null);
    var target = checkTerrain == null ? thumbnail : terrainThumbnail;
    var pool = checkTerrain == null ? readbacks : terrainReadbacks;
    int width = target.width, height = target.height, scale = width / WIDTH;
    var client = Minecraft.getInstance();
    long tick = client.level == null ? -1 : client.level.getGameTime();
    int playerTick = client.player == null ? -1 : client.player.tickCount;
    long frame = ++presented;
    long capturedNanos = FrameCapture.monotonicNanos();
    var camera = FrameCapture.worldCamera();
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    encoder.clearColorTexture(target.getColorTexture(), new Vector4f());
    try (var pass = encoder.createRenderPass(() -> "Portalix frame thumbnail",
        target.getColorTextureView(), Optional.empty(), null, OptionalDouble.empty())) {
      pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("InSampler", source.getColorTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(3, 1, 0, 0);
    }
    var readback = pool.pollFirst();
    if (readback == null)
      readback = RenderSystem.getDevice().createBuffer(() -> "Portalix presented frame readback",
          GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * Integer.BYTES);
    var buffer = readback;
    pending++;
    encoder.copyTextureToBuffer(target.getColorTexture(), buffer, 0, () -> {
      try (var mapped = buffer.map(true, false)) {
        var data = mapped.data();
        if (checkTerrain != null && failure == null) {
          int[] argb = new int[width * height];
          for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
              argb[y * width + x] = ARGB.opaque(ARGB.fromABGR(readPixel(data, width, height, x, y)));
          var captured = new FrameCapture.Frame(frame, capturedNanos, tick, playerTick, width, height,
              argb, camera, null, "presented-terrain-thumbnail");
          try {
            checkTerrain.accept(captured);
          } catch (AssertionError | RuntimeException problem) {
            Files.createDirectories(directory);
            var artifact = directory.resolve("failed-terrain-frame-" + frame + ".png");
            ImageIO.write(captured.toBufferedImage(), "png", artifact.toFile());
            failure = new AssertionError("Terrain check failed in " + artifact, problem);
          }
        }
        int nonzero = 0, samples = 0;
        int[] pixels = new int[58 * 21];
        // Exclude the hand, hotbar, progress caption and top-right toasts.
        for (int y = 4; y < 25; y++) for (int x = 3; x < 61; x++) {
          int pixel = readPixel(data, width, height, x * scale, y * scale);
          if ((pixel & 0xffffff) != 0) nonzero++;
          pixels[samples] = pixel;
          samples++;
        }
        boolean collapsed = frame > previousFrame && sameCamera(camera, previousCamera)
            && lostWorld(previousPixels, pixels);
        if (failure == null && (nonzero < samples / 100 || collapsed)) {
          Files.createDirectories(directory);
          Path artifact = directory.resolve("black-presented-frame-" + frame + ".png");
          try (var image = new NativeImage(WIDTH, HEIGHT, false)) {
            for (int y = 0; y < HEIGHT; y++)
              for (int x = 0; x < WIDTH; x++)
                image.setPixelABGR(x, y, ARGB.opaque(readPixel(data, width, height, x * scale, y * scale)));
            image.writeToFile(artifact);
          }
          var diagnostic = new LinkedHashMap<String, Object>();
          diagnostic.put("frame", frame);
          diagnostic.put("capturedNanos", capturedNanos);
          diagnostic.put("camera", cameraState(camera));
          diagnostic.put("previousFrame", previousFrame);
          diagnostic.put("previousCamera", cameraState(previousCamera));
          diagnostic.put("sampledPixels", samples);
          diagnostic.put("nonzeroPixels", nonzero);
          diagnostic.put("collapsed", collapsed);
          Files.writeString(directory.resolve("black-presented-frame-" + frame + ".json"),
              new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(diagnostic));
          failure = new AssertionError("Black world image in presented gameplay frame " + frame
              + (collapsed ? " (previously lit world disappeared at a stationary camera)" : "") + ": " + artifact);
        }
        if (frame > previousFrame) {
          previousFrame = frame;
          previousPixels = pixels;
          previousCamera = camera;
        }
        checked++;
      } catch (IOException problem) {
        if (failure == null) failure = problem;
      } finally {
        pending--;
        pool.addLast(buffer);
        releaseWhenIdle();
      }
    }, 0);
  }

  private static int readPixel(ByteBuffer data, int width, int height, int x, int y) {
    return data.getInt((x + (height - 1 - y) * width) * Integer.BYTES);
  }

  void check() {
    if (failure != null && !failureReported) {
      var problem = failure;
      failureReported = true;
      throw new AssertionError("Presented frame check failed", problem);
    }
  }

  long checked() { return checked; }
  long presented() { return presented; }
  boolean idle() { return pending == 0; }

  private static Map<String, Object> cameraState(FrameCapture.CameraSnapshot camera) {
    if (camera == null) return null;
    var position = camera.position();
    return Map.of("position", List.of(position.x, position.y, position.z), "pitch", camera.pitch(),
        "yaw", camera.yaw(), "projection", camera.projection().get(new float[16]),
        "viewRotation", camera.viewRotation().get(new float[16]), "zeroToOneDepth", camera.zeroToOne());
  }

  private static boolean sameCamera(FrameCapture.CameraSnapshot current, FrameCapture.CameraSnapshot before) {
    return current != null && before != null && current.position().distanceTo(before.position()) < .002
        && Math.abs(current.pitch() - before.pitch()) < .01 && Math.abs(current.yaw() - before.yaw()) < .01
        && current.projection().equals(before.projection(), .0001f)
        && current.viewRotation().equals(before.viewRotation(), .0001f);
  }

  static boolean lostWorld(int[] before, int[] current) {
    if (before == null) return false;
    int lit = 0, lost = 0;
    for (int i = 0; i < current.length; i++) {
      int a = before[i], b = current[i];
      int previous = Math.max(a >> 16 & 255, Math.max(a >> 8 & 255, a & 255));
      int now = Math.max(b >> 16 & 255, Math.max(b >> 8 & 255, b & 255));
      if (previous >= 48) {
        lit++;
        if (now < 12) lost++;
      }
    }
    // Bright clouds must not conceal a world layer that became black beneath them.
    return lit >= current.length / 3 && lost > current.length / 4 && lost > lit * .55;
  }

  CompletableFuture<Void> finish() {
    closing = true;
    releaseWhenIdle();
    return completion;
  }
  void release() { releaseWhenIdle(); }

  private void releaseWhenIdle() {
    if (!closing || pending != 0) return;
    if (thumbnail != null) {
      thumbnail.destroyBuffers();
      thumbnail = null;
      readbacks.forEach(GpuBuffer::close);
      readbacks.clear();
    }
    if (terrainThumbnail != null) {
      terrainThumbnail.destroyBuffers();
      terrainThumbnail = null;
      terrainReadbacks.forEach(GpuBuffer::close);
      terrainReadbacks.clear();
    }
    if (completion.isDone()) return;
    if (failure != null) completion.completeExceptionally(failure);
    else if (checked != presented)
      completion.completeExceptionally(new AssertionError("Checked " + checked + " of " + presented + " presented frames"));
    else completion.complete(null);
  }
}

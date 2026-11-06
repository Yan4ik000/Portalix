package dev.portalix.test.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;

/** A small GPU readback of every presented gameplay frame catches flashes between review samples. */
final class PresentedFrameGuard {
  private final Path directory;
  private RenderTarget thumbnail;
  private final ArrayDeque<GpuBuffer> readbacks = new ArrayDeque<>();
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

  void presented(RenderTarget source) {
    RenderSystem.assertOnRenderThread();
    check();
    if (closing) { releaseWhenIdle(); return; }
    if (thumbnail == null)
      thumbnail = new TextureTarget("Portalix presented frame audit", 64, 36, false, GpuFormat.RGBA8_UNORM);
    long frame = ++presented;
    long capturedNanos = FrameCapture.monotonicNanos();
    var camera = FrameCapture.worldCamera();
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    encoder.clearColorTexture(thumbnail.getColorTexture(), new Vector4f());
    try (var pass = encoder.createRenderPass(() -> "Portalix presented RGB audit",
        thumbnail.getColorTextureView(), Optional.empty(), null, OptionalDouble.empty())) {
      pass.setPipeline(RenderPipelines.TRACY_BLIT);
      RenderSystem.bindDefaultUniforms(pass);
      pass.bindTexture("InSampler", source.getColorTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(3, 1, 0, 0);
    }
    var readback = readbacks.pollFirst();
    if (readback == null)
      readback = RenderSystem.getDevice().createBuffer(() -> "Portalix presented frame readback",
          GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 64L * 36 * Integer.BYTES);
    var buffer = readback;
    pending++;
    encoder.copyTextureToBuffer(thumbnail.getColorTexture(), buffer, 0, () -> {
      try (var mapped = buffer.map(true, false)) {
        var data = mapped.data();
        int nonzero = 0, samples = 0;
        int[] pixels = new int[58 * 21];
        // Exclude the hand, hotbar, progress caption and top-right toasts.
        for (int y = 4; y < 25; y++) for (int x = 3; x < 61; x++) {
          int pixel = data.getInt((x + (35 - y) * 64) * Integer.BYTES);
          if ((pixel & 0xffffff) != 0) nonzero++;
          pixels[samples] = pixel;
          samples++;
        }
        boolean collapsed = frame > previousFrame && sameCamera(camera, previousCamera)
            && lostWorld(previousPixels, pixels);
        if (failure == null && (nonzero < samples / 100 || collapsed)) {
          Files.createDirectories(directory);
          Path artifact = directory.resolve("black-presented-frame-" + frame + ".png");
          try (var image = new NativeImage(64, 36, false)) {
            for (int y = 0; y < 36; y++) for (int x = 0; x < 64; x++)
              image.setPixelABGR(x, y, data.getInt((x + (35 - y) * 64) * Integer.BYTES) | 0xff000000);
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
        readbacks.addLast(buffer);
        releaseWhenIdle();
      }
    }, 0);
  }

  void check() {
    if (failure != null && !failureReported) {
      var problem = failure;
      failureReported = true;
      throw new AssertionError("Presented frame audit failed", problem);
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
    if (closing && pending == 0 && thumbnail != null) {
      thumbnail.destroyBuffers();
      thumbnail = null;
      readbacks.forEach(GpuBuffer::close);
      readbacks.clear();
    }
    if (closing && pending == 0 && !completion.isDone()) {
      if (failure != null) completion.completeExceptionally(failure);
      else if (checked != presented)
        completion.completeExceptionally(new AssertionError("Not every presented frame was audited: " + checked + "/" + presented));
      else completion.complete(null);
    }
  }
}

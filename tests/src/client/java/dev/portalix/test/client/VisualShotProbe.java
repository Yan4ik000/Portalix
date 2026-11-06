package dev.portalix.test.client;

import com.google.gson.GsonBuilder;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.render.ParticleView;
import dev.portalix.network.gun.GunShotPayload;
import dev.portalix.network.gun.PortalImpactPayload;
import dev.portalix.item.PortalixVariant;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Observes real effects; only pixels in the presented main image establish visual success. */
public final class VisualShotProbe {
  public record Spec(AABB flightVolume, AABB impactVolume, AABB controlVolume, Vec3 expectedHit,
                     boolean orange, Path evidenceDirectory, PortalixVariant variant) {
    public Spec(AABB flightVolume, AABB impactVolume, AABB controlVolume, Vec3 expectedHit,
                boolean orange, Path evidenceDirectory) {
      this(flightVolume, impactVolume, controlVolume, expectedHit, orange, evidenceDirectory, PortalixVariant.STANDARD);
    }
    public Spec {
      Objects.requireNonNull(flightVolume);
      Objects.requireNonNull(impactVolume);
      Objects.requireNonNull(controlVolume);
      Objects.requireNonNull(expectedHit);
      Objects.requireNonNull(evidenceDirectory);
      Objects.requireNonNull(variant);
    }
  }

  private record Pixels(int changed, int spanX, int spanY, int hueChanges,
                        int negativeResidualRejected, int outsideTrajectoryRejected) {}
  public record FrameTicket(long presentation, int playerTick, double partialTick,
                            double flightAge, double impactAge, String phase) {}
  private record Sample(long frameId, long presentationNanos, long tick, int playerTick, String phase, double partialTick,
                        long presentation, Pixels flight,
                        Pixels impact, FrameCapture.Difference control, boolean stableCamera) {}
  private record EventReceipt(long gameTick, int playerTick, long receivedNanos) {
    double eventPlayerTick(long serverTick) {
      return playerTick - Math.max(0, gameTick - serverTick);
    }
  }

  private static VisualShotProbe active;
  private final FrameCapture.Frame baseline;
  private final Spec spec;
  private final List<Sample> samples = new ArrayList<>();
  private final List<String> violations = new ArrayList<>();
  private final java.util.function.IntPredicate hue;
  private GunShotPayload shot;
  private PortalImpactPayload impact;
  private EventReceipt shotReceipt;
  private EventReceipt impactReceipt;
  private double submittedTime = Double.NEGATIVE_INFINITY;
  private int submittedPlayerTick = -1;
  private int lastRequestedPlayerTick = -1;
  private String lastRequestedPhase = "";
  private long presented;
  private int flightCaptures;
  private int impactCaptures;
  private boolean settledRequested;
  private int maximumFlight;
  private int maximumImpact;
  private int maximumObservedFlight = -1;
  private int maximumObservedImpact = -1;
  private FrameCapture.Frame bestFlight;
  private FrameCapture.Frame bestImpact;
  private FrameCapture.Frame observedFlight;
  private FrameCapture.Frame observedImpact;
  private FrameCapture.Frame settled;
  private Pixels settledFlight;
  private Pixels settledImpact;
  private CompletableFuture<List<String>> completion;

  private VisualShotProbe(FrameCapture.Frame baseline, Spec spec) {
    this.baseline = baseline;
    this.spec = spec;
    int color = spec.variant.particleColor(spec.orange);
    float expectedHue = hue(color);
    hue = pixel -> {
      int r = pixel >> 16 & 255, g = pixel >> 8 & 255, b = pixel & 255;
      int maximum = Math.max(r, Math.max(g, b)), minimum = Math.min(r, Math.min(g, b));
      float difference = Math.abs(hue(pixel) - expectedHue);
      return maximum > 33 && maximum - minimum > maximum * .28f
          && Math.min(difference, 1 - difference) < .10f;
    };
    requireWorldFrame(baseline);
    regions(baseline);
  }

  private static float hue(int color) {
    int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
    int maximum = Math.max(r, Math.max(g, b)), minimum = Math.min(r, Math.min(g, b));
    float difference = maximum - minimum;
    if (difference == 0) return 0;
    float sector = maximum == r ? (g - b) / difference
        : maximum == g ? 2 + (b - r) / difference : 4 + (r - g) / difference;
    return (sector < 0 ? sector + 6 : sector) / 6;
  }

  public static VisualShotProbe arm(FrameCapture.Frame baseline, Spec spec) {
    if (active != null) throw new IllegalStateException("A visual shot probe is already armed");
    if (!Minecraft.getInstance().gui.hud.isHidden())
      throw new IllegalStateException("Capture the shot baseline in normal F1 view: HUD and hand must be hidden");
    active = new VisualShotProbe(Objects.requireNonNull(baseline), Objects.requireNonNull(spec));
    return active;
  }

  public static void shotFired(GunShotPayload data) {
    Minecraft client = Minecraft.getInstance();
    if (active == null || client.player == null || data.playerId() != client.player.getId()) return;
    VisualShotProbe probe = active;
    if (probe.shot != null) {
      if (probe.shot.sequence() != data.sequence()) probe.violations.add("More than one real shot arrived while the probe was armed");
      return;
    }
    probe.shot = data;
    probe.shotReceipt = new EventReceipt(client.level.getGameTime(), client.player.tickCount, FrameCapture.monotonicNanos());
  }

  public static void portalImpact(PortalImpactPayload data) {
    Minecraft client = Minecraft.getInstance();
    if (active == null || active.shot == null || data.sequence() != active.shot.sequence() || active.impact != null) return;
    active.impact = data;
    active.impactReceipt = new EventReceipt(client.level.getGameTime(), client.player.tickCount, FrameCapture.monotonicNanos());
  }

  public static void submitted(ParticleView view) {
    Minecraft client = Minecraft.getInstance();
    if (active == null || active.shot == null || client.player == null || PortalSceneManager.isPortalView()) return;
    active.submittedPlayerTick = client.player.tickCount;
    active.submittedTime = (double) client.player.tickCount + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
  }

  /** Select and timestamp the effect phase at the actual main blit, before asynchronous readback. */
  public FrameTicket presentedFrame(String blocker) {
    Minecraft client = Minecraft.getInstance();
    if (completion != null || done() || client.player == null) return null;
    long index = ++presented;
    double partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    boolean submitted = shot != null && submittedPlayerTick == client.player.tickCount;
    double flightAge = submitted ? submittedTime - shotReceipt.eventPlayerTick(shot.firedServerTick()) : -1;
    double impactAge = submitted && impact != null ? submittedTime - impactReceipt.eventPlayerTick(impact.serverTick()) : -1;
    String phase = "";
    if (submitted) {
      phase = phase(flightAge, impactAge, impact != null);
      if (submittedPlayerTick == lastRequestedPlayerTick && phase.equals(lastRequestedPhase)) phase = "";
    }
    if (phase.isEmpty() || !blocker.isEmpty()) return null;
    switch (phase) {
      case "flight" -> flightCaptures++;
      case "impact" -> impactCaptures++;
      case "settled" -> settledRequested = true;
      default -> throw new IllegalArgumentException("Unknown shot capture phase " + phase);
    }
    lastRequestedPlayerTick = client.player.tickCount;
    lastRequestedPhase = phase;
    return new FrameTicket(index, client.player.tickCount, partial, flightAge, impactAge, phase);
  }

  private String phase(double flightAge, double impactAge, boolean impactReceived) {
    if (!impactReceived) return bestFlight == null && flightCaptures < 5
        && flightAge >= 2.25 && flightAge < shot.impactAfterTicks() ? "flight" : "";
    if (impactAge >= 26) {
      int playerTick = Minecraft.getInstance().player.tickCount;
      return !settledRequested && playerTick - impactReceipt.eventPlayerTick(impact.serverTick()) >= 26 ? "settled" : "";
    }
    return bestImpact == null && impactCaptures < 5 && impactAge >= .75 && impactAge <= 8 ? "impact" : "";
  }

  public String caption(FrameTicket ticket) {
    return switch (ticket.phase) {
      case "flight" -> "EVENT: real shot in flight";
      case "impact" -> "EVENT: real failed-surface impact sparks";
      case "settled" -> "EVENT: shot particles expired";
      default -> throw new IllegalArgumentException("Unknown shot capture phase " + ticket.phase);
    };
  }

  /** Classify the captured image using its effect phase at copy time, independent of readback latency. */
  public void accept(FrameCapture.Frame frame, FrameTicket ticket) {
    if (completion != null || shot == null) throw new IllegalStateException("No pending visual shot observation");
    if (frame.playerTick() != ticket.playerTick)
      throw new IllegalArgumentException("The effect ticket and captured frame describe different game ticks");
    requireWorldFrame(frame);
    if (frame.id() <= baseline.id()) throw new IllegalArgumentException("The sample predates its baseline");
    if (samples.size() >= 11) throw new IllegalStateException("Visual shot sample budget exceeded");
    if (!Minecraft.getInstance().gui.hud.isHidden()) violations.add("HUD/hand became visible during the particle-only observation");
    boolean stable = frame.cameraPosition().distanceTo(baseline.cameraPosition()) <= .02
        && Math.abs(Mth.wrapDegrees(frame.cameraYaw() - baseline.cameraYaw())) <= .1
        && Math.abs(frame.cameraPitch() - baseline.cameraPitch()) <= .1;
    if (!stable) violations.add("The camera moved between baseline and frame " + frame.id());
    var regions = regions(frame);
    var control = frame.difference(baseline, regions[2], 24);
    boolean quiet = control.meanChannelDifference() < 12 && control.changedFraction() < .15;
    if (!quiet) violations.add("The control region changed in frame " + frame.id() + ": " + control);
    Pixels flightPixels = changedPixels(frame, regions[0], true);
    Pixels impactPixels = changedPixels(frame, regions[1], false);
    String phase = ticket.phase;
    if ("settled".equals(phase)) {
      settled = frame;
      settledFlight = flightPixels;
      settledImpact = impactPixels;
    } else if ("impact".equals(phase)) {
      if (impactPixels.changed > maximumObservedImpact) {
        maximumObservedImpact = impactPixels.changed;
        observedImpact = frame;
      }
      if (stable && quiet && sprite(impactPixels, 5) && impactPixels.changed > maximumImpact) {
        maximumImpact = impactPixels.changed;
        bestImpact = frame;
      }
    } else {
      if (!"flight".equals(phase)) throw new IllegalArgumentException("Unknown shot capture phase " + phase);
      if (flightPixels.changed > maximumObservedFlight) {
        maximumObservedFlight = flightPixels.changed;
        observedFlight = frame;
      }
      // The muzzle flash ends before 0.8 ticks; the projectile launches at 2 ticks.
      if (ticket.flightAge >= 2.25 && ticket.flightAge < shot.impactAfterTicks() && stable && quiet
          && sprite(flightPixels, 8) && flightPixels.changed > maximumFlight) {
        maximumFlight = flightPixels.changed;
        bestFlight = frame;
      }
    }
    samples.add(new Sample(frame.id(), frame.presentationNanos(), frame.gameTick(), frame.playerTick(), phase, ticket.partialTick,
        ticket.presentation, flightPixels, impactPixels, control, stable));
  }

  public boolean done() { return settled != null; }

  /** Save evidence before assertions, so an unsuccessful probe remains reviewable. */
  public CompletableFuture<List<String>> finish() {
    if (completion != null) return completion;
    if (active == this) active = null;
    completion = CompletableFuture.supplyAsync(() -> {
      writeEvidence();
      verify();
      return List.of("visual/real projectile appears in the main image after muzzle flash and before impact",
          "visual/real failed-surface impact sparks disappear after their lifetime");
    }, Util.ioPool());
    return completion;
  }

  private FrameCapture.Region[] regions(FrameCapture.Frame frame) {
    FrameCapture.Region flight = project(frame, spec.flightVolume);
    FrameCapture.Point aim = frame.project(spec.expectedHit).orElseThrow(() -> new AssertionError("The authored target is outside the camera"));
    // Bound the authored straight corridor around its aim, excluding unrelated actors elsewhere in the world AABB.
    int halfWidth = frame.width() / 24, halfHeight = frame.height() / 6;
    FrameCapture.Region corridor = new FrameCapture.Region((int) aim.x() - halfWidth, (int) aim.y() - halfHeight,
        halfWidth * 2, halfHeight * 2);
    flight = intersect(flight, intersect(corridor, frame.whole()));
    FrameCapture.Region impact = project(frame, spec.impactVolume);
    FrameCapture.Region control = project(frame, spec.controlVolume);
    if (overlaps(control, flight) || overlaps(control, impact))
      throw new AssertionError("The authored control region overlaps shot evidence on screen");
    return new FrameCapture.Region[] {flight, impact, control};
  }

  private Pixels changedPixels(FrameCapture.Frame frame, FrameCapture.Region region, boolean flight) {
    int count = 0, minX = frame.width(), minY = frame.height(), maxX = -1, maxY = -1;
    int hueChanges = 0, negativeResidual = 0, outsideTrajectory = 0;
    Trajectory trajectory = flight ? trajectory(frame) : null;
    for (int y = region.y(); y < region.y() + region.height(); y++)
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        int pixel = frame.pixel(x, y), before = baseline.pixel(x, y);
        int red = (pixel >>> 16 & 255) - (before >>> 16 & 255);
        int green = (pixel >>> 8 & 255) - (before >>> 8 & 255);
        int blue = (pixel & 255) - (before & 255);
        int delta = Math.max(Math.abs(red), Math.max(Math.abs(green), Math.abs(blue)));
        if (!hue.test(pixel) || delta < 24) continue;
        hueChanges++;
        // Additive emission brightens the background. A moving white cloud exposing blue sky darkens R/G.
        if (red < -3 || green < -3 || blue < -3 || .2126 * red + .7152 * green + .0722 * blue <= 0) {
          negativeResidual++;
          continue;
        }
        if (trajectory != null && !trajectory.contains(x + .5, y + .5)) {
          outsideTrajectory++;
          continue;
        }
        count++; minX = Math.min(minX, x); minY = Math.min(minY, y);
        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
      }
    return new Pixels(count, count == 0 ? 0 : maxX - minX + 1, count == 0 ? 0 : maxY - minY + 1,
        hueChanges, negativeResidual, outsideTrajectory);
  }

  private Trajectory trajectory(FrameCapture.Frame frame) {
    Vec3 auxiliary = shot.start().add(shot.launchDirection().normalize().scale(shot.start().distanceTo(spec.expectedHit)));
    return new Trajectory(projectPoint(frame, shot.start()), projectPoint(frame, auxiliary),
        projectPoint(frame, spec.expectedHit), frame.height() / 44.0);
  }

  private static FrameCapture.Point projectPoint(FrameCapture.Frame frame, Vec3 point) {
    return frame.projectUnclipped(point).orElseThrow(() -> new AssertionError("Shot trajectory control point is outside the authored camera depth range: " + point));
  }

  /** A projected control hull bounds the flight path without consulting its simulation or generated vertices. */
  private record Trajectory(FrameCapture.Point start, FrameCapture.Point auxiliary, FrameCapture.Point end, double radius) {
    boolean contains(double x, double y) {
      double a = cross(start, auxiliary, x, y), b = cross(auxiliary, end, x, y), c = cross(end, start, x, y);
      if (Math.abs(cross(start, auxiliary, end.x(), end.y())) > 1e-6
          && ((a >= 0 && b >= 0 && c >= 0) || (a <= 0 && b <= 0 && c <= 0))) return true;
      return Math.min(distanceSquared(start, auxiliary, x, y), Math.min(distanceSquared(auxiliary, end, x, y),
          distanceSquared(end, start, x, y))) <= radius * radius;
    }

    private static double cross(FrameCapture.Point a, FrameCapture.Point b, double x, double y) {
      return (b.x() - a.x()) * (y - a.y()) - (b.y() - a.y()) * (x - a.x());
    }

    private static double distanceSquared(FrameCapture.Point a, FrameCapture.Point b, double x, double y) {
      double dx = b.x() - a.x(), dy = b.y() - a.y(), length = dx * dx + dy * dy;
      double t = length == 0 ? 0 : Math.clamp(((x - a.x()) * dx + (y - a.y()) * dy) / length, 0, 1);
      double offsetX = x - a.x() - t * dx, offsetY = y - a.y() - t * dy;
      return offsetX * offsetX + offsetY * offsetY;
    }
  }

  private static boolean sprite(Pixels pixels, int minimumPixels) {
    return pixels.changed >= minimumPixels && pixels.spanX >= 3 && pixels.spanY >= 2;
  }

  private void verify() {
    check(violations.isEmpty(), "Invalid shot observation: " + violations);
    check(shot != null && shot.orange() == spec.orange, "No real shot with the expected color was observed");
    check(shot.variant() == spec.variant, "The real shot used another gun palette");
    check(shot.end().distanceTo(spec.expectedHit) < 1, "Shot hit an unexpected surface: " + shot.end());
    check(impact != null && impact.sequence() == shot.sequence(), "No matching real impact event");
    check(impact.orange() == shot.orange() && impact.variant() == shot.variant(), "Shot and impact palettes disagree");
    check(!impact.placed(), "Negative target accepted a portal; persistent aperture pixels contaminate the oracle");
    check(impact.position().distanceTo(spec.expectedHit) < 1, "Impact did not reach the authored negative target");
    check(bestFlight != null, "No extended shot-colored projectile was visible between launch and impact");
    check(bestImpact != null, "No extended shot-colored impact sparks were visible in the main window");
    check(settled != null, "The lifetime was not observed through a settled frame");
    check(settledFlight.changed <= Math.max(2, maximumFlight / 10), "Projectile pixels remained after the lifetime: " + settledFlight);
    check(settledImpact.changed <= Math.max(2, maximumImpact / 10), "Impact pixels remained after the lifetime: " + settledImpact);
  }

  private void writeEvidence() {
    try {
      Files.createDirectories(spec.evidenceDirectory);
      save("shot-baseline", baseline);
      save("shot-projectile", bestFlight == null ? observedFlight : bestFlight);
      save("shot-impact", bestImpact == null ? observedImpact : bestImpact);
      save("shot-settled", settled);
      Map<String, Object> proof = new LinkedHashMap<>();
      proof.put("flightVolume", spec.flightVolume); proof.put("impactVolume", spec.impactVolume);
      proof.put("controlVolume", spec.controlVolume); proof.put("expectedHit", spec.expectedHit);
      proof.put("expectedOrange", spec.orange); proof.put("shot", shot); proof.put("impact", impact);
      proof.put("shotReceipt", shotReceipt); proof.put("impactReceipt", impactReceipt);
      proof.put("samples", samples); proof.put("violations", violations);
      Files.writeString(spec.evidenceDirectory.resolve("shot-proof.json"), new GsonBuilder().setPrettyPrinting().create().toJson(proof));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private void save(String name, FrameCapture.Frame frame) throws IOException {
    if (frame == null) return;
    Path png = spec.evidenceDirectory.resolve(name + ".png");
    if (Files.exists(png)) throw new IOException("Shot evidence already exists: " + png);
    if (!ImageIO.write(frame.toBufferedImage(), "png", png.toFile())) throw new IOException("No PNG writer is available");
    Files.writeString(spec.evidenceDirectory.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(frame.metadata()));
  }

  private void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message + " [shot evidence=" + spec.evidenceDirectory + "]");
  }

  private static void requireWorldFrame(FrameCapture.Frame frame) {
    if (!"main-window-before-blit".equals(frame.metadata().get("source")))
      throw new IllegalArgumentException("The particle oracle requires the actual main window image");
  }

  private static FrameCapture.Region project(FrameCapture.Frame frame, AABB box) {
    return frame.project(box).orElseThrow(() -> new AssertionError("Authored shot region is outside the window: " + box));
  }

  private static FrameCapture.Region intersect(FrameCapture.Region first, FrameCapture.Region second) {
    int x = Math.max(first.x(), second.x()), y = Math.max(first.y(), second.y());
    int right = Math.min(first.x() + first.width(), second.x() + second.width());
    int bottom = Math.min(first.y() + first.height(), second.y() + second.height());
    if (right <= x || bottom <= y) throw new AssertionError("Authored shot regions do not intersect the window");
    return new FrameCapture.Region(x, y, right - x, bottom - y);
  }

  private static boolean overlaps(FrameCapture.Region first, FrameCapture.Region second) {
    return first.x() < second.x() + second.width() && second.x() < first.x() + first.width()
        && first.y() < second.y() + second.height() && second.y() < first.y() + first.height();
  }
}

package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.io.UncheckedIOException;
import com.google.gson.GsonBuilder;
import java.util.function.IntPredicate;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

final class EntityScenePixels {
  private static final AABB QUIET_QUARTZ = new AABB(158, 99.99, 508, 159, 100.01, 510);
  private record Foreground(int pixels, int corePixels, FrameCapture.Region bounds, double centerX, double centerY,
                            double luminanceDeviation) {}
  private EntityScenePixels() {}

  static Map<String, Object> visible(FrameCapture.Frame image, AABB bounds, EntitySystemFixture.Kind kind,
                                     boolean excludeHand, FrameCapture.Frame... absent) {
    return visible(image, bounds, kind, excludeHand, QUIET_QUARTZ, absent);
  }

  static Map<String, Object> visible(FrameCapture.Frame image, AABB bounds, EntitySystemFixture.Kind kind,
                                     boolean excludeHand, AABB quietQuartz, FrameCapture.Frame... absent) {
    var projected = image.project(bounds).orElseThrow(() -> new AssertionError("Native " + kind + " actor is outside its frame"));
    int right = Math.min(projected.x() + projected.width(), excludeHand ? image.width() * 3 / 4 : image.width());
    int bottom = Math.min(projected.y() + projected.height(), excludeHand ? image.height() * 3 / 4 : image.height());
    check(right > projected.x() && bottom > projected.y(), "Native actor is hidden entirely by the gun/hand region: " + kind);
    var region = new FrameCapture.Region(projected.x(), projected.y(), right - projected.x(), bottom - projected.y());
    var controls = new java.util.ArrayList<Map<String, Object>>();
    for (var empty : absent) {
      check(image.cameraPosition().distanceTo(empty.cameraPosition()) < .002
          && Math.abs(Mth.wrapDegrees(image.cameraYaw() - empty.cameraYaw())) < .05
          && Math.abs(image.cameraPitch() - empty.cameraPitch()) < .05,
          "Native actor/absence images used different cameras: " + kind);
      var control = control(image, quietQuartz);
      var referenceControl = control(empty, quietQuartz);
      check(!overlap(control, region), "The authored quartz control overlaps the native actor body: " + kind);
      var difference = image.difference(empty, control, 24);
      check(difference.changedFraction() < .03 && difference.meanChannelDifference() < 5,
          "The static quartz background changed independently of native " + kind + ": " + difference);
      controls.add(Map.of("referenceFrameId", empty.id(), "worldBounds", quietQuartz,
          "region", control, "referenceRegion", referenceControl, "difference", difference,
          "nativeHandExcluded", true, "hudExcluded", true));
    }
    if (kind == EntitySystemFixture.Kind.CHICKEN || kind == EntitySystemFixture.Kind.SNOWBALL
        || kind == EntitySystemFixture.Kind.ARROW) return neutralBody(image, region, kind, controls, absent);
    IntPredicate material = switch (kind) {
      case ITEM, TRIDENT -> FrameCapture.Material.CYAN;
      case PIG -> pixel -> {
        int r = pixel >> 16 & 255, g = pixel >> 8 & 255, b = pixel & 255;
        return r >= 60 && r > g * 1.15 && r > b * 1.05;
      };
      default -> throw new IllegalArgumentException("No fixed material signature for " + kind);
    };
    int count = 0, left = image.width(), top = image.height(), maxX = -1, maxY = -1;
    for (int y = region.y(); y < region.y() + region.height(); y++)
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        int pixel = image.pixel(x, y);
        if (!material.test(pixel)) continue;
        boolean foreground = true;
        for (var empty : absent) if (difference(pixel, empty.pixel(x, y)) < 24) { foreground = false; break; }
        if (!foreground) continue;
        count++;
        left = Math.min(left, x); top = Math.min(top, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
      }
    int minimum = kind == EntitySystemFixture.Kind.TRIDENT ? 6 : 20;
    check(count >= minimum && maxX - left >= 2 && maxY - top >= 2,
        "Native " + kind + " body has no localized expected-material foreground: pixels=" + count
            + ", extent=" + left + "," + top + ".." + maxX + "," + maxY + ", region=" + region);
    check(image.statistics(region, pixel -> true).luminanceDeviation() > 5,
        "Native " + kind + " projected body has no textured foreground");
    var proof = new LinkedHashMap<String, Object>();
    proof.put("frame", image.metadata()); proof.put("kind", kind.name()); proof.put("bodyRegion", region);
    proof.put("materialPixels", count); proof.put("materialBounds", new int[] {left, top, maxX + 1, maxY + 1});
    proof.put("minimumMaterialPixels", minimum); proof.put("backgroundControls", controls);
    proof.put("handRegionExcluded", excludeHand);
    return proof;
  }

  private static Map<String, Object> neutralBody(FrameCapture.Frame image, FrameCapture.Region region,
                                                 EntitySystemFixture.Kind kind,
                                                 java.util.List<Map<String, Object>> controls,
                                                 FrameCapture.Frame[] absent) {
    int width = Math.max(8, region.width() / 2);
    boolean onRight = region.x() + region.width() / 2 >= image.width() / 2;
    int x = onRight ? region.x() + region.width() + 4 : region.x() - width - 4;
    check(x >= 0 && x + width <= image.width(), "Native " + kind + " has no adjacent background control");
    var local = new FrameCapture.Region(x, region.y(), width, region.height());
    double backgroundNoise = 0;
    var localControls = new java.util.ArrayList<Map<String, Object>>();
    for (var empty : absent) {
      var difference = image.difference(empty, local, 24);
      check(difference.changedFraction() < .03 && difference.meanChannelDifference() < 5,
          "Native " + kind + " adjacent background changed independently of its body: " + difference);
      backgroundNoise = Math.max(backgroundNoise, difference.changedFraction());
      localControls.add(Map.of("referenceFrameId", empty.id(), "region", local, "difference", difference));
    }
    boolean[] foreground = new boolean[region.area()];
    for (int y = 0; y < region.height(); y++) for (int column = 0; column < region.width(); column++) {
      int pixel = image.pixel(region.x() + column, region.y() + y);
      boolean changed = true;
      for (var empty : absent)
        if (difference(pixel, empty.pixel(region.x() + column, region.y() + y)) < 24) { changed = false; break; }
      foreground[y * region.width() + column] = changed;
    }
    Foreground body = null;
    int[] queue = new int[foreground.length];
    int[][] directions = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    for (int seed = 0; seed < foreground.length; seed++) {
      if (!foreground[seed]) continue;
      int head = 0, tail = 0, corePixels = 0, left = region.width(), top = region.height(), right = -1, bottom = -1;
      double sumX = 0, sumY = 0, luminance = 0, squares = 0;
      foreground[seed] = false;
      queue[tail++] = seed;
      while (head < tail) {
        int point = queue[head++], column = point % region.width(), y = point / region.width();
        left = Math.min(left, column); top = Math.min(top, y);
        right = Math.max(right, column); bottom = Math.max(bottom, y);
        sumX += column; sumY += y;
        if (column >= region.width() * .4 && column <= region.width() * .6
            && y >= region.height() * .2 && y <= region.height() * .8) corePixels++;
        int pixel = image.pixel(region.x() + column, region.y() + y);
        double value = .2126 * (pixel >> 16 & 255) + .7152 * (pixel >> 8 & 255) + .0722 * (pixel & 255);
        luminance += value; squares += value * value;
        for (int[] direction : directions) {
          int neighborX = column + direction[0], neighborY = y + direction[1];
          if (neighborX < 0 || neighborX >= region.width() || neighborY < 0 || neighborY >= region.height()) continue;
          int neighbor = neighborY * region.width() + neighborX;
          if (!foreground[neighbor]) continue;
          foreground[neighbor] = false;
          queue[tail++] = neighbor;
        }
      }
      double centerX = sumX / tail, centerY = sumY / tail;
      if (centerX < region.width() * .25 || centerX > region.width() * .75 || corePixels < 3) continue;
      if (body == null || tail > body.pixels()) {
        body = new Foreground(tail, corePixels, new FrameCapture.Region(region.x() + left, region.y() + top,
            right - left + 1, bottom - top + 1), region.x() + centerX, region.y() + centerY,
            Math.sqrt(Math.max(0, squares / tail - Math.pow(luminance / tail, 2))));
      }
    }
    int minimum = kind == EntitySystemFixture.Kind.CHICKEN ? 20 : 6;
    check(body != null && body.pixels() >= minimum && body.bounds().width() >= 3 && body.bounds().height() >= 3
        && body.luminanceDeviation() > 3
        && body.pixels() > backgroundNoise * region.area() * 3,
        "Native " + kind + " has no coherent textured central foreground distinct from its local background: " + body);
    var proof = new LinkedHashMap<String, Object>();
    proof.put("frame", image.metadata()); proof.put("kind", kind.name());
    proof.put("bodyRegion", region); proof.put("foreground", body);
    proof.put("foregroundMethod", "connected central texture differs from every native absence frame");
    proof.put("backgroundControls", controls); proof.put("adjacentBackgroundControls", localControls);
    return proof;
  }

  private static int difference(int first, int second) {
    return Math.max(Math.abs((first >> 16 & 255) - (second >> 16 & 255)),
        Math.max(Math.abs((first >> 8 & 255) - (second >> 8 & 255)), Math.abs((first & 255) - (second & 255))));
  }

  private static FrameCapture.Region control(FrameCapture.Frame frame, AABB quietQuartz) {
    var region = frame.project(quietQuartz)
        .orElseThrow(() -> new AssertionError("The authored quiet quartz control is outside the native frame"));
    var hand = new FrameCapture.Region(frame.width() / 2, frame.height() / 2,
        frame.width() - frame.width() / 2, frame.height() - frame.height() / 2);
    var bottomHud = new FrameCapture.Region(0, frame.height() * 5 / 6,
        frame.width(), frame.height() - frame.height() * 5 / 6);
    var reticle = new FrameCapture.Region(frame.width() / 2 - 40, frame.height() / 2 - 40, 80, 80);
    var toasts = new FrameCapture.Region(frame.width() * 3 / 4, 0,
        frame.width() - frame.width() * 3 / 4, frame.height() / 5);
    check(region.area() > 200 && !overlap(region, hand) && !overlap(region, bottomHud)
        && !overlap(region, reticle) && !overlap(region, toasts),
        "Authored quartz control intersects hand/HUD or has insufficient native pixels: " + region
            + ", camera=" + frame.cameraPosition() + ", yaw=" + frame.cameraYaw() + ", pitch=" + frame.cameraPitch());
    return region;
  }

  private static boolean overlap(FrameCapture.Region a, FrameCapture.Region b) {
    return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
        && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
  }

  static void save(Path path, Map<String, ?> evidence) {
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(evidence));
    }
    catch (IOException failure) { throw new UncheckedIOException(failure); }
  }
}

package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

final class PortalSceneAssertions {
  record Pixels(int samples, int matched, Optional<FrameCapture.Region> extent) {
    double fraction() { return (double) matched / samples; }
  }
  record ProjectedMarker(FrameCapture.Point expected, FrameCapture.Point observed, int visiblePixels, int matchedPixels) {}
  private record ApertureMask(Vec3 normal, Vec3 right, Vec3 up, double depth, double eyeRight, double eyeUp) {
    ApertureMask(PortalEndpoint portal, Vec3 eye) {
      this(portal.normal(), portal.right(), portal.up(), portal.center().subtract(eye).dot(portal.normal()),
          eye.subtract(portal.center()).dot(portal.right()), eye.subtract(portal.center()).dot(portal.up()));
    }

    boolean contains(Vector4f ray) {
      double denominator = normal.x * ray.x + normal.y * ray.y + normal.z * ray.z;
      if (Math.abs(denominator) < 1e-12) return false;
      double distance = depth / denominator;
      if (distance <= 0) return false;
      double x = eyeRight + distance * (right.x * ray.x + right.y * ray.y + right.z * ray.z);
      double y = eyeUp + distance * (up.x * ray.x + up.y * ray.y + up.z * ray.z);
      double uv = PortalAperture.SURFACE_UV_SCALE;
      return uv * uv * (4 * x * x + y * y) < .7 * .7;
    }
  }

  private PortalSceneAssertions() {}

  static ProjectedMarker projectedMarker(FrameCapture.Frame frame, AABB imageBounds,
                                         FrameCapture.Material material, PortalEndpoint entrance) {
    var region = intersection(frame.project(imageBounds).orElseThrow(), aperture(frame, entrance, .05));
    var mask = coreMask(frame, region, entrance);
    var metadata = frame.metadata();
    var inverse = new Matrix4f().set((float[]) metadata.get("worldProjectionColumnMajor"))
        .mul(new Matrix4f().set((float[]) metadata.get("viewRotationColumnMajor"))).invert();
    var eye = frame.cameraPosition();
    double range = eye.distanceTo(imageBounds.getCenter())
        + imageBounds.getXsize() + imageBounds.getYsize() + imageBounds.getZsize();
    var ray = new Vector4f();
    int expected = 0, matched = 0;
    double expectedX = 0, expectedY = 0, matchedX = 0, matchedY = 0;
    for (int y = region.y(); y < region.y() + region.height(); y++) {
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        if (!mask[(y - region.y()) * region.width() + x - region.x()]) continue;
        inverse.transform(ray.set((float) (2 * (x + .5) / frame.width() - 1),
            (float) (1 - 2 * (y + .5) / frame.height()), 1, 1));
        ray.div(ray.w);
        var direction = new Vec3(ray.x, ray.y, ray.z).normalize();
        if (imageBounds.clip(eye, eye.add(direction.scale(range))).isEmpty()) continue;
        expected++; expectedX += x + .5; expectedY += y + .5;
        if (material.test(frame.pixel(x, y))) {
          matched++; matchedX += x + .5; matchedY += y + .5;
        }
      }
    }
    check(expected >= 20 && matched >= expected * .7,
        "The visible authored marker silhouette disagrees with the portal image: "
            + material + " matched=" + matched + "/" + expected + ": " + frame.png());
    return new ProjectedMarker(new FrameCapture.Point(expectedX / expected, expectedY / expected),
        new FrameCapture.Point(matchedX / matched, matchedY / matched), expected, matched);
  }

  static Pixels pixels(FrameCapture.Frame frame, FrameCapture.Region region, IntPredicate material,
                       PortalEndpoint... apertures) {
    boolean[] mask = coreMask(frame, region, apertures);
    int samples = 0, matched = 0, minX = frame.width(), minY = frame.height(), maxX = -1, maxY = -1;
    for (int y = region.y(); y < region.y() + region.height(); y++) {
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        if (!mask[(y - region.y()) * region.width() + x - region.x()]) continue;
        samples++;
        if (!material.test(frame.pixel(x, y))) continue;
        matched++;
        minX = Math.min(minX, x); minY = Math.min(minY, y);
        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
      }
    }
    check(samples > 0, "The authored image region misses the portal clear core: " + region);
    return new Pixels(samples, matched, matched == 0 ? Optional.empty()
        : Optional.of(new FrameCapture.Region(minX, minY, maxX - minX + 1, maxY - minY + 1)));
  }

  static FrameCapture.Difference difference(FrameCapture.Frame current, FrameCapture.Frame before,
                                            FrameCapture.Region region, int threshold, PortalEndpoint... apertures) {
    check(current.width() == before.width() && current.height() == before.height(),
        "Paired portal observations have different framebuffer sizes");
    boolean[] mask = coreMask(current, region, apertures);
    long total = 0;
    int samples = 0, changed = 0;
    for (int y = region.y(); y < region.y() + region.height(); y++) {
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        if (!mask[(y - region.y()) * region.width() + x - region.x()]) continue;
        int a = current.pixel(x, y), b = before.pixel(x, y);
        int red = Math.abs((a >> 16 & 255) - (b >> 16 & 255));
        int green = Math.abs((a >> 8 & 255) - (b >> 8 & 255));
        int blue = Math.abs((a & 255) - (b & 255));
        total += red + green + blue;
        if (Math.max(red, Math.max(green, blue)) > threshold) changed++;
        samples++;
      }
    }
    check(samples > 0, "The paired image region misses the portal clear core: " + region);
    return new FrameCapture.Difference((double) total / (samples * 3L), (double) changed / samples, changed);
  }

  static boolean[] coreMask(FrameCapture.Frame frame, FrameCapture.Region region,
                            PortalEndpoint... apertures) {
    var metadata = frame.metadata();
    var inverse = new Matrix4f().set((float[]) metadata.get("worldProjectionColumnMajor"))
        .mul(new Matrix4f().set((float[]) metadata.get("viewRotationColumnMajor"))).invert();
    check(inverse.isFinite(), "Cannot project the actual frame into its authored portal clear core");
    var masks = new ApertureMask[apertures.length];
    for (int i = 0; i < masks.length; i++) masks[i] = new ApertureMask(apertures[i], frame.cameraPosition());
    boolean[] mask = new boolean[region.area()];
    var ray = new Vector4f();
    for (int y = region.y(); y < region.y() + region.height(); y++) {
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        inverse.transform(ray.set((float) (2 * (x + .5) / frame.width() - 1),
            (float) (1 - 2 * (y + .5) / frame.height()), 1, 1));
        ray.div(ray.w);
        boolean inside = true;
        for (var aperture : masks) if (!aperture.contains(ray)) { inside = false; break; }
        mask[(y - region.y()) * region.width() + x - region.x()] = inside;
      }
    }
    return mask;
  }

  static Pixels sprite(FrameCapture.Frame frame, FrameCapture.Region region, IntPredicate material, int minimumPixels,
                       PortalEndpoint... apertures) {
    Pixels pixels = pixels(frame, region, material, apertures);
    check(pixels.matched >= minimumPixels, "Clear-core material " + material + " has " + pixels.matched
        + " pixels, expected at least " + minimumPixels + " in " + region + ": " + frame.png());
    var extent = pixels.extent.orElseThrow();
    check(extent.width() >= 2 && extent.height() >= 2, "Clear-core material extent is too small: " + extent);
    return pixels;
  }

  static List<Map<String, Object>> landmarks(FrameCapture.Frame frame, PortalSystemFixture.Arrangement scene) {
    var aperture = aperture(frame, scene.entrance(), .05);
    var proof = new ArrayList<Map<String, Object>>();
    for (var marker : scene.markers()) {
      var projected = frame.project(scene.imageBounds(marker.bounds())).orElseThrow(
          () -> new AssertionError("The authored remote marker projects outside the real image: " + marker));
      var region = intersection(projected, aperture);
      Pixels pixels;
      try {
        pixels = sprite(frame, region, marker.material(), 8, scene.entrance());
      } catch (AssertionError failure) {
        throw new AssertionError("Remote landmark " + marker.material() + " at " + marker.bounds()
            + " is absent from its authored main-window region " + region, failure);
      }
      proof.add(Map.of("material", marker.material().name(), "worldBounds", marker.bounds().toString(),
          "imageBounds", scene.imageBounds(marker.bounds()).toString(), "region", region,
          "matchedPixels", pixels.matched(), "fraction", pixels.fraction()));
    }
    return proof;
  }

  static FrameCapture.Region aperture(FrameCapture.Frame frame, PortalEndpoint portal, double margin) {
    Vec3 right = portal.right().scale(.5 - margin), up = portal.up().scale(1 - margin);
    AABB bounds = new AABB(portal.center(), portal.center());
    for (int x : new int[] {-1, 1}) for (int y : new int[] {-1, 1}) {
      Vec3 point = portal.center().add(right.scale(x)).add(up.scale(y));
      bounds = bounds.minmax(new AABB(point, point));
    }
    return frame.project(bounds.inflate(.00001)).orElseThrow(
        () -> new AssertionError("The authored portal aperture is outside the actual camera"));
  }

  static FrameCapture.Region intersection(FrameCapture.Region first, FrameCapture.Region second) {
    int x = Math.max(first.x(), second.x()), y = Math.max(first.y(), second.y());
    int right = Math.min(first.x() + first.width(), second.x() + second.width());
    int bottom = Math.min(first.y() + first.height(), second.y() + second.height());
    check(right > x && bottom > y, "The remote marker does not intersect the authored aperture");
    return new FrameCapture.Region(x, y, right - x, bottom - y);
  }

  static void projection(FrameCapture.Frame frame) {
    for (String matrix : List.of("worldProjectionColumnMajor", "viewRotationColumnMajor")) {
      var elements = (float[]) frame.metadata().get(matrix);
      check(elements != null && elements.length == 16, "The actual world projection is missing");
      for (float element : elements) check(Float.isFinite(element), "The actual camera matrix is nonfinite");
    }
    var projection = (float[]) frame.metadata().get("worldProjectionColumnMajor");
    check(projection[0] > 0 && projection[5] > 0 && projection[14] > 0,
        "The actual perspective/near plane is degenerate");
    frame.assertNotBlack(new FrameCapture.Region(frame.width() / 10, frame.height() / 10,
        frame.width() * 4 / 5, frame.height() * 3 / 5));
  }
}

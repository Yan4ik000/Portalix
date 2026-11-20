package dev.portalix.client.portal.clip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class PortalFrustumPlanesTest {
  private static final float[][] ANGLES = {{0, 0, 0}, {90, 35, 0}};

  @Test
  void frustumClassificationMatchesTheProjectedView() {
    var intersection = new FrustumIntersection();
    for (boolean zeroToOne : new boolean[] {false, true}) {
      for (float[] angles : ANGLES) {
        var orientation = new Quaternionf().rotationYXZ(radians(angles[0]), radians(angles[1]), radians(angles[2]));
        var view = new Matrix4f().rotation(new Quaternionf(orientation).conjugate());
        var matrix = new Matrix4f().perspective(radians(70), 16f / 9, .05f, 128, zeroToOne).mul(view);
        PortalFrustumPlanes.set(intersection, matrix);
        var normalized = new FrustumIntersection(matrix);
        for (float z : new float[] {-10, -1, 1}) {
          for (float x : new float[] {-50, 0, 50}) {
            var point = orientation.transform(new Vector3f(x, .7f, z));
            var clip = matrix.transform(new Vector4f(point, 1));
            boolean inClip = clip.x >= -clip.w && clip.x <= clip.w && clip.y >= -clip.w && clip.y <= clip.w
                && clip.z >= -clip.w && clip.z <= clip.w;
            assertEquals(inClip, intersection.testPoint(point), "Point classification follows the actual native clip half-spaces");
            assertEquals(normalized.intersectAab(point.x - .23f, point.y - .17f, point.z - .31f,
                    point.x + .23f, point.y + .17f, point.z + .31f),
                intersection.intersectAab(point.x - .23f, point.y - .17f, point.z - .31f,
                    point.x + .23f, point.y + .17f, point.z + .31f),
                "AABB visibility follows the projected view");
          }
        }
      }
    }
  }

  private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
}

package dev.portalix.client.gun.animation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class GunAnimationSetTest {
  @Test
  void samplingInterpolatesTranslationAndRotationAndHonorsLoopAndEndpoints() {
    var library = new GunAnimationSet(Map.of("motion", new float[][] {
        {0, 2, 4, 0, 0, 0, 1}, {2, 4, 6, 0, 0, 1, 0}
    }), 2);
    assertEquals(.5f, library.duration("motion"));
    float[] middle = library.pose("motion", .25f, false);
    assertArrayEquals(new float[] {1, 3, 5}, new float[] {middle[0], middle[1], middle[2]}, 1e-6f);
    var rotated = new Quaternionf(middle[3], middle[4], middle[5], middle[6]).transform(new Vector3f(1, 0, 0));
    assertTrue(rotated.distance(new Vector3f(0, 1, 0)) < 1e-5, "half of a 180-degree turn must rotate by 90 degrees");
    assertArrayEquals(library.pose("motion", 0, false), library.pose("motion", -1, false));
    assertArrayEquals(library.pose("motion", .5f, false), library.pose("motion", 20, false));
    assertArrayEquals(middle, library.pose("motion", .75f, true), 1e-6f);
    assertArrayEquals(library.pose("motion", 0, false), library.pose("motion", .5f, true), 1e-6f);
  }

  @Test
  void additiveLayerUsesTheReferenceDeltaAndComposesRotationsInBoneSpace() {
    float angle = (float) (Math.PI / 2);
    var baseRotation = new Quaternionf().rotationY(angle);
    var referenceRotation = new Quaternionf().rotationX(angle);
    var actionRotation = new Quaternionf(referenceRotation).rotateZ(angle);
    float[] base = {10, 20, 30, baseRotation.x, baseRotation.y, baseRotation.z, baseRotation.w};
    float[] reference = {1, 2, 3, referenceRotation.x, referenceRotation.y, referenceRotation.z, referenceRotation.w};
    float[] action = {4, 0, 8, actionRotation.x, actionRotation.y, actionRotation.z, actionRotation.w};
    GunAnimationSet.additive(base, reference, action, new Quaternionf(), new Quaternionf());
    assertArrayEquals(new float[] {13, 18, 35}, new float[] {base[0], base[1], base[2]}, 1e-6f);
    var rotation = new Quaternionf(base[3], base[4], base[5], base[6]);
    assertEquals(1, rotation.lengthSquared(), 1e-6);
    assertTrue(rotation.transform(new Vector3f(1, 0, 0)).distance(new Vector3f(0, 1, 0)) < 1e-5);
    assertTrue(rotation.transform(new Vector3f(0, 1, 0)).distance(new Vector3f(0, 0, 1)) < 1e-5);
    float[] unchanged = base.clone();
    GunAnimationSet.additive(base, reference, reference.clone(), new Quaternionf(), new Quaternionf());
    assertArrayEquals(unchanged, base, "a neutral additive layer must not disturb the current pose");
  }
}

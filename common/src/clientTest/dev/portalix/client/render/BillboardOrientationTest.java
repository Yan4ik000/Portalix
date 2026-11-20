package dev.portalix.client.render;

import static dev.portalix.test.ClientPortalFixtures.near;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class BillboardOrientationTest {
  @Test
  void billboardsFaceTheActiveViewAcrossModelTransforms() {
    for (float scale : new float[] {.1f, 1, 8}) {
      for (float angle : new float[] {0, .5f, 1.57f, 3.1f}) {
        var matrix = new Matrix4f().translate(4, -3, 6).rotateXYZ(angle, angle * .3f, -angle).scale(scale);
        var orientation = new BillboardOrientation(matrix);
        var center = new Vector3f(1, 2, -3);
        var basis = orientation.at(center);
        var right = matrix.transformDirection(new Vector3f(basis.right())).normalize();
        var up = matrix.transformDirection(new Vector3f(basis.up())).normalize();
        var facing = matrix.transformPosition(new Vector3f(center)).normalize();
        assertTrue(Math.abs(right.dot(up)) < 1e-5 && Math.abs(right.dot(facing)) < 1e-5
            && Math.abs(up.dot(facing)) < 1e-5, "billboard faces active view and stays orthogonal");
        near(Vec3.ZERO, new Vec3(matrix.transformPosition(orientation.camera())), 1e-4, "camera transformed into model space");
      }
    }
  }
}

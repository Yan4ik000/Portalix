package dev.portalix.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class BillboardOrientation {
  private final Matrix4f modelToView;
  private final Matrix4f viewToModel;
  private final float unitScale;

  public BillboardOrientation(Matrix4f modelToView) {
    this.modelToView = modelToView;
    this.viewToModel = new Matrix4f(modelToView).invert();
    this.unitScale = (float) Math.cbrt(Math.abs(modelToView.determinant()));
  }

  public Vector3f camera() {
    return this.viewToModel.transformPosition(new Vector3f());
  }

  public Basis at(Vector3f center) {
    Vector3f right = new Vector3f();
    Vector3f up = new Vector3f();
    at(center, right, up, new Vector3f());
    return new Basis(right, up);
  }

  public void at(Vector3f center, Vector3f right, Vector3f up, Vector3f normal) {
    this.modelToView.transformPosition(normal.set(center));
    if (normal.lengthSquared() < 1.0E-8) {
      normal.set(0.0F, 0.0F, -1.0F);
    }

    normal.normalize();
    right
        .set(normal)
        .cross(
            0.0F,
            Math.abs(normal.y) > 0.99F ? 0.0F : 1.0F,
            Math.abs(normal.y) > 0.99F ? 1.0F : 0.0F)
        .normalize();
    up.set(right).cross(normal).normalize();
    this.viewToModel.transformDirection(right).mul(this.unitScale);
    this.viewToModel.transformDirection(up).mul(this.unitScale);
  }

  public record Basis(Vector3f right, Vector3f up) {}
}

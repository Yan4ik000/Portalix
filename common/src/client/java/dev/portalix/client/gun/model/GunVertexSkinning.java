package dev.portalix.client.gun.model;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

final class GunVertexSkinning {
  static final Matrix4fc SOURCE_TO_MODEL =
      new Matrix4f(-1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1);

  private GunVertexSkinning() {}

  static void skin(float[] source, Matrix4f[] matrices, float[] result) {
    Vector3f position = new Vector3f();
    Vector3f normal = new Vector3f();
    Vector3f p = new Vector3f();
    Vector3f n = new Vector3f();
    int i = 0;

    for (int o = 0; i < source.length; o += 8) {
      position.zero();
      normal.zero();

      int single = -1;
      int influences = 0;
      for (int j = 8; j < 14; j += 2) {
        if (source[i + j + 1] != 0.0F) {
          single = j;
          influences++;
        }
      }
      if (influences == 1) {
        Matrix4f matrix = matrices[(int) source[i + single]];
        matrix.transformPosition(position.set(source[i], source[i + 1], source[i + 2]));
        position.mul(source[i + single + 1]);
        matrix.transformDirection(normal.set(source[i + 3], source[i + 4], source[i + 5]));
        normal.mul(source[i + single + 1]);
      } else {
        for (int j = 8; j < 14; j += 2) {
          float weight = source[i + j + 1];
          if (weight != 0.0F) {
            Matrix4f m = matrices[(int) source[i + j]];
            m.transformPosition(p.set(source[i], source[i + 1], source[i + 2]));
            m.transformDirection(n.set(source[i + 3], source[i + 4], source[i + 5]));
            position.fma(weight, p);
            normal.fma(weight, n);
          }
        }
      }
      normal.normalize();
      result[o] = -position.x;
      result[o + 1] = position.z;
      result[o + 2] = position.y;
      result[o + 3] = -normal.x;
      result[o + 4] = normal.z;
      result[o + 5] = normal.y;
      result[o + 6] = source[i + 6];
      result[o + 7] = source[i + 7];
      i += 14;
    }
  }
}

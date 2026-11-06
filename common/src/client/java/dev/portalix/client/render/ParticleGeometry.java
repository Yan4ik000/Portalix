package dev.portalix.client.render;

import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

public final class ParticleGeometry {
  public static void strip(
      Pose pose,
      VertexConsumer b,
      Vector3f a,
      Vector3f end,
      Vector3f sideA,
      Vector3f sideB,
      float radiusA,
      float radiusB,
      float v0,
      float v1,
      int r,
      int g,
      int blue,
      int alphaA,
      int alphaB) {
    strip(
        pose, b, a, end, sideA, sideB, radiusA, radiusB, v0, v1, r, g, blue, r, g, blue, alphaA,
        alphaB);
  }

  public static void strip(
      Pose pose,
      VertexConsumer b,
      Vector3f a,
      Vector3f end,
      Vector3f sideA,
      Vector3f sideB,
      float radiusA,
      float radiusB,
      float v0,
      float v1,
      int rA,
      int gA,
      int blueA,
      int rB,
      int gB,
      int blueB,
      int alphaA,
      int alphaB) {
    for (int c = 0; c < 4; c++) {
      boolean first = c < 2;
      Vector3f point = first ? a : end;
      Vector3f side = first ? sideA : sideB;
      float scale = (c != 0 && c != 3 ? 1 : -1) * (first ? radiusA : radiusB);
      b.addVertex(
              pose.pose(),
              point.x + side.x * scale,
              point.y + side.y * scale,
              point.z + side.z * scale)
          .setColor(
              first ? rA : rB, first ? gA : gB, first ? blueA : blueB, first ? alphaA : alphaB)
          .setUv(c != 0 && c != 3 ? 1.0F : 0.0F, first ? v0 : v1)
          .setOverlay(0)
          .setLight(15728880)
          .setNormal(0.0F, 1.0F, 0.0F);
    }
  }

  public static void sprite(
      Pose pose,
      VertexConsumer b,
      Vector3f center,
      Vector3f right,
      Vector3f up,
      float radius,
      int r,
      int g,
      int blue,
      int alpha) {
    for (int c = 0; c < 4; c++) {
      float x = (c != 0 && c != 3 ? 1 : -1) * radius;
      float y = (c < 2 ? -1 : 1) * radius;
      b.addVertex(
              pose.pose(),
              center.x + right.x * x + up.x * y,
              center.y + right.y * x + up.y * y,
              center.z + right.z * x + up.z * y)
          .setColor(r, g, blue, alpha)
          .setUv(c != 0 && c != 3 ? 1.0F : 0.0F, c < 2 ? 1.0F : 0.0F)
          .setOverlay(0)
          .setLight(15728880)
          .setNormal(0.0F, 1.0F, 0.0F);
    }
  }
}

package dev.portalix.portal.traversal;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PortalProjectileRotationCodecTest {
  @Test
  void frozenLocalResidualRetainsItsRotation() {
    var data = JsonParser.parseString("[-0.70710677,0.0,0.0,0.70710677]");
    var rotation = PortalProjectileRotation.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow();
    assertEquals(1, rotation.lengthSquared(), 1e-6);
    assertTrue(rotation.transform(new Vector3f(0, 1, 0)).equals(new Vector3f(0, 0, -1), 1e-6f));
    var encoded = PortalProjectileRotation.CODEC.encodeStart(JsonOps.INSTANCE, rotation).getOrThrow();
    var decoded = PortalProjectileRotation.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    assertEquals(rotation, decoded);
  }

  @Test
  void nativeQuaternionNormalizationCannotAdmitInvalidPersistentState() {
    for (var values : new float[][] {
        {0, 0, 0, 0}, {Float.NaN, 0, 0, 1}, {0, Float.POSITIVE_INFINITY, 0, 1},
        {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}
    }) {
      var data = new JsonArray();
      for (float value : values) data.add(value);
      assertTrue(PortalProjectileRotation.CODEC.parse(JsonOps.INSTANCE, data).error().isPresent(), data::toString);
    }
  }
}

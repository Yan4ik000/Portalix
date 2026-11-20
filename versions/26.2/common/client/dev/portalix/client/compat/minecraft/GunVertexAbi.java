package dev.portalix.client.compat.minecraft;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;

public final class GunVertexAbi {
  public static final VertexFormat FORMAT = VertexFormat.builder(0)
      .addAttribute("Position", GpuFormat.RGB32_FLOAT)
      .addAttribute("Color", GpuFormat.RGBA8_UNORM)
      .addAttribute("UV0", GpuFormat.RG32_FLOAT)
      .addAttribute("UV1", GpuFormat.RG16_SINT)
      .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
      .build();

  private GunVertexAbi() {}

  public static boolean mutableAttributes(VertexFormat format) {
    var light = format.getElement("UV2");
    var entity = format.getElement("iris_Entity");
    validate(format, light, GpuFormat.RG16_SINT);
    validate(format, entity, GpuFormat.RGBA16_UINT);
    return light != null || entity != null;
  }

  private static void validate(VertexFormat format, VertexFormatElement element, GpuFormat expected) {
    if (element != null && (element.format() != expected || element.offset() < 0
        || element.offset() % Short.BYTES != 0
        || element.offset() > format.getVertexSize() - expected.blockSize())) {
      throw new IllegalArgumentException("Unsupported gun vertex attribute " + element
          + "; expected " + expected + " within vertex stride " + format.getVertexSize());
    }
  }

  public static boolean update(ByteBuffer encoded, VertexFormat format,
      int light, int nextLight, long context, long nextContext) {
    var lightElement = light != nextLight ? format.getElement("UV2") : null;
    var entityElement = context != nextContext ? format.getElement("iris_Entity") : null;
    if (lightElement == null && entityElement == null) return false;
    for (int vertex = 0; vertex < encoded.remaining(); vertex += format.getVertexSize()) {
      if (lightElement != null) {
        int offset = vertex + lightElement.offset();
        encoded.putShort(offset, (short) nextLight);
        encoded.putShort(offset + 2, (short) (nextLight >>> 16));
      }
      if (entityElement != null) {
        int offset = vertex + entityElement.offset();
        encoded.putShort(offset, (short) (nextContext >>> 32));
        encoded.putShort(offset + 2, (short) (nextContext >>> 16));
        encoded.putShort(offset + 4, (short) nextContext);
      }
    }
    return true;
  }
}

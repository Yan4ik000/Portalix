package dev.portalix.client.compat.minecraft;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;

public final class GunVertexAbi {
  public static final VertexFormat FORMAT = VertexFormat.builder()
      .add("Position", VertexFormatElement.POSITION)
      .add("Color", VertexFormatElement.COLOR)
      .add("UV0", VertexFormatElement.UV0)
      .add("UV1", VertexFormatElement.UV1)
      .add("Normal", VertexFormatElement.NORMAL)
      .padding(1)
      .build();

  private GunVertexAbi() {}

  public static boolean mutableAttributes(VertexFormat format) {
    var light = element(format, "UV2");
    var entity = element(format, "iris_Entity");
    validate(format, light, VertexFormatElement.Type.SHORT, 2);
    validate(format, entity, VertexFormatElement.Type.USHORT, 4);
    return light != null || entity != null;
  }

  private static VertexFormatElement element(VertexFormat format, String name) {
    int index = format.getElementAttributeNames().indexOf(name);
    return index < 0 ? null : format.getElements().get(index);
  }

  private static void validate(VertexFormat format, VertexFormatElement element,
      VertexFormatElement.Type type, int count) {
    if (element == null) return;
    int offset = format.getOffset(element);
    if (element.type() != type || element.count() != count || element.normalized()
        || offset < 0 || offset % Short.BYTES != 0
        || offset > format.getVertexSize() - element.byteSize()) {
      throw new IllegalArgumentException("Unsupported gun vertex attribute " + element
          + " in " + format);
    }
  }

  public static boolean update(ByteBuffer encoded, VertexFormat format,
      int light, int nextLight, long context, long nextContext) {
    var lightElement = light != nextLight ? element(format, "UV2") : null;
    var entityElement = context != nextContext ? element(format, "iris_Entity") : null;
    if (lightElement == null && entityElement == null) return false;
    for (int vertex = 0; vertex < encoded.remaining(); vertex += format.getVertexSize()) {
      if (lightElement != null) {
        int offset = vertex + format.getOffset(lightElement);
        encoded.putShort(offset, (short) nextLight);
        encoded.putShort(offset + 2, (short) (nextLight >>> 16));
      }
      if (entityElement != null) {
        int offset = vertex + format.getOffset(entityElement);
        encoded.putShort(offset, (short) (nextContext >>> 32));
        encoded.putShort(offset + 2, (short) (nextContext >>> 16));
        encoded.putShort(offset + 4, (short) nextContext);
      }
    }
    return true;
  }
}

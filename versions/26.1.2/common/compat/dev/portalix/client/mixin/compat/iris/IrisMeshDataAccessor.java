package dev.portalix.client.mixin.compat.iris;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MeshData.class)
interface IrisMeshDataAccessor {
  @Accessor("indexBuffer")
  void portalix$indexBuffer(ByteBufferBuilder.Result indices);
}

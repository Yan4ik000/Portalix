package dev.portalix.client.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.MeshData;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderType.class)
abstract class PortalEntityRenderTypeMixin {
  @WrapMethod(method = "draw")
  private void portalix$clipDraw(MeshData mesh, Operation<Void> original) {
    var mask = PortalEntityClipping.mask((RenderType) (Object) this);
    if (mask == null) original.call(mesh);
    else PortalClipping.withEntity(mask, () -> original.call(mesh));
  }
}

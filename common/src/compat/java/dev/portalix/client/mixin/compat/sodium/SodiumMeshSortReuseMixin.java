package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.sodium.SodiumSortPlanesAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
abstract class SodiumMeshSortReuseMixin {
  @Definition(
      id = "getGeometryPlanes",
      method =
          "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/data/DynamicSorter;getGeometryPlanes()Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/GeometryPlanes;")
  @Expression("?.getGeometryPlanes() == null")
  @ModifyExpressionValue(method = "execute", at = @At("MIXINEXTRAS:EXPRESSION"), allow = 1)
  private boolean portalix$reusePublishedOrder(boolean missing, @Local DynamicData data) {
    return missing && ((SodiumSortPlanesAccess) data).portalix$initialSort() == null;
  }
}

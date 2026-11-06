package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.minecraft.VanillaSceneTerrain;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
abstract class TerrainRenderPreparationMixin {
  @Unique private static final ChunkSectionLayer[] PORTALIX_LAYERS = ChunkSectionLayer.values();
  @Unique private Matrix4f portalix$frameMatrix;
  @Unique private long portalix$frameTime;

  @ModifyExpressionValue(
      method = "prepareChunkRenders",
      at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
          target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
  private ObjectArrayList<SectionRenderDispatcher.RenderSection> portalix$rasterSections(
      ObjectArrayList<SectionRenderDispatcher.RenderSection> sections) {
    return VanillaSceneTerrain.renderSections(sections);
  }

  @Inject(method = "prepareChunkRenders", at = @At("HEAD"))
  private void portalix$frameTransform(
      Matrix4fc matrix, CallbackInfoReturnable<ChunkSectionsToRender> cir) {

    portalix$frameMatrix = new Matrix4f(matrix);
    portalix$frameTime = Util.getMillis();
  }

  @WrapOperation(
      method = "prepareChunkRenders",
      at = @At(value = "NEW", target = "(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;"),
      slice = @Slice(
          from = @At(value = "NEW", target = "net/minecraft/client/renderer/DynamicUniforms$ChunkSectionInfo"),
          to = @At(value = "INVOKE", target =
              "Lnet/minecraft/client/renderer/DynamicUniforms$ChunkSectionInfo;<init>(Lorg/joml/Matrix4fc;IIIFII)V")),
      allow = 1)
  private Matrix4f portalix$sectionTransform(Matrix4fc matrix, Operation<Matrix4f> original) {
    return portalix$frameMatrix;
  }

  @WrapOperation(
      method = "prepareChunkRenders",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;values()[Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;"))
  private ChunkSectionLayer[] portalix$layers(Operation<ChunkSectionLayer[]> original) {
    return PORTALIX_LAYERS;
  }

  @WrapOperation(
      method = "prepareChunkRenders",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
  private long portalix$frameTime(Operation<Long> original) {
    return portalix$frameTime;
  }
}

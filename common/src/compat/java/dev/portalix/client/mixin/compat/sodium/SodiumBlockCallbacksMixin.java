package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.function.Predicate;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.services.PlatformModelEmitter;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = BlockRenderer.class, priority = 900, remap = false)
abstract class SodiumBlockCallbacksMixin extends AbstractBlockRenderContext {
  @Unique private final Predicate<Direction> portalix$cullTest = this::isFaceCulled;

  @Unique
  private final PlatformModelEmitter.Bufferer portalix$defaultBuffer = this::bufferDefaultModel;

  @Definition(id = "isFaceCulled", method =
      "Lnet/caffeinemc/mods/sodium/client/render/model/AbstractBlockRenderContext;isFaceCulled(Lnet/minecraft/core/Direction;)Z")
  @Expression("this::isFaceCulled")
  @ModifyExpressionValue(
      method = "renderModel",
      at = @At("MIXINEXTRAS:EXPRESSION"), allow = 1)
  private Predicate<Direction> portalix$cullCallback(Predicate<Direction> original) {
    return portalix$cullTest;
  }

  @Definition(id = "bufferDefaultModel", method =
      "Lnet/caffeinemc/mods/sodium/client/render/model/AbstractBlockRenderContext;bufferDefaultModel(Lnet/minecraft/client/renderer/block/dispatch/BlockStateModelPart;Ljava/util/function/Predicate;Ljava/util/function/Consumer;)V")
  @Expression("this::bufferDefaultModel")
  @ModifyExpressionValue(
      method = "renderModel",
      at = @At("MIXINEXTRAS:EXPRESSION"), allow = 1)
  private PlatformModelEmitter.Bufferer portalix$bufferCallback(PlatformModelEmitter.Bufferer original) {
    return portalix$defaultBuffer;
  }
}

package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisPipelineHistory;
import java.util.function.Supplier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.uniforms.MatrixUniforms$Previous", remap = false)
abstract class IrisPreviousMatrixMixin {
  @Shadow @Final private Supplier<Matrix4fc> parent;
  @Shadow private Matrix4f previous;
  @Unique private Object portalix$historyToken;

  @Inject(method = "get()Lorg/joml/Matrix4fc;", at = @At("HEAD"))
  private void portalix$seedPreviousMatrix(CallbackInfoReturnable<Matrix4fc> ci) {
    var token = IrisPipelineHistory.currentToken();
    if (token == portalix$historyToken) return;
    portalix$historyToken = token;
    // Each shader samples its own supplier lazily, potentially after the reset frame.
    previous.set(parent.get());
  }
}

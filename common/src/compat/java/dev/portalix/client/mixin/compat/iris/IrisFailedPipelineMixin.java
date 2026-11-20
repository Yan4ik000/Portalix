package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.Iris;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Iris.class)
abstract class IrisFailedPipelineMixin {
  @WrapWithCondition(method = "createPipeline", at = @At(value = "INVOKE",
      target = "Lnet/irisshaders/iris/Iris;handleException(Ljava/lang/Exception;)V"), allow = 1)
  private static boolean portalix$reportPrimaryFailure(Exception failure) {
    return !IrisScene.preparing();
  }

  @ModifyArg(method = "createPipeline", at = @At(value = "INVOKE",
      target = "Lnet/irisshaders/iris/IrisLogging;error(Ljava/lang/String;Ljava/lang/Throwable;)V"),
      index = 0, allow = 1)
  private static String portalix$describeFailure(String original) {
    return IrisScene.preparing() ? "Failed to create shader pipeline for the portal view" : original;
  }

  @WrapWithCondition(
      method = "createPipeline",
      at = @At(value = "INVOKE", target =
          "Lnet/irisshaders/iris/gl/buffer/ShaderStorageBufferHolder;forceDeleteBuffers()V"),
      allow = 1)
  private static boolean portalix$preserveActiveBuffers() {
    return !IrisScene.preparing();
  }

  @WrapWithCondition(
      method = "createPipeline",
      at = @At(value = "FIELD", target = "Lnet/irisshaders/iris/Iris;fallback:Z", opcode = Opcodes.PUTSTATIC),
      allow = 1)
  private static boolean portalix$preservePrimaryStatus(boolean fallback) {
    return !IrisScene.preparing();
  }
}

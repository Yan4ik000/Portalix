package dev.portalix.client.mixin.compat.iris;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.iris.IrisPipelineHistory;
import dev.portalix.client.compat.iris.IrisScene;
import dev.portalix.client.compat.iris.IrisStorageHistory;
import dev.portalix.client.compat.iris.IrisTargetHistory;
import dev.portalix.client.compat.iris.IrisViewHistory;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.mixinterface.RenderTargetInterface;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.ClearPass;
import net.irisshaders.iris.targets.RenderTargets;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IrisRenderingPipeline.class)
abstract class IrisPipelineHistoryMixin implements IrisPipelineHistory {
  @Shadow @Final private RenderTargets renderTargets;
  @Shadow @Final private Set<GlImage> customImages;
  @Shadow @Final private CenterDepthSampler centerDepthSampler;
  @Shadow @Final private ComputeProgram[] shadowComputes;
  @Shadow @Final private int shadowMapResolution;
  @Shadow private ShadowRenderTargets shadowRenderTargets;
  @Shadow private ImmutableList<ClearPass> shadowClearPassesFull;
  @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;
  @Unique private static final ComputeProgram[] portalix$noComputes = {};
  @Unique private boolean portalix$resetViewHistory;
  @Unique private Object portalix$uniformHistoryToken = new Object();
  @Unique private ImmutableList<ClearPass> portalix$deferredShadowClears;

  @Override
  public void portalix$invalidateHistory() {
    ((IrisTargetHistory) renderTargets).portalix$invalidateHistory();
    portalix$resetViewHistory = true;
    portalix$uniformHistoryToken = new Object();
  }

  @Override
  public Object portalix$uniformHistoryToken() {
    return portalix$uniformHistoryToken;
  }

  @Inject(method = "beginLevelRendering", at = @At("HEAD"))
  private void portalix$restorePersistentResources(CallbackInfo ci) {
    if (!portalix$resetViewHistory) return;
    ((IrisViewHistory) centerDepthSampler).portalix$invalidateHistory();
    if (shaderStorageBufferHolder != null)
      ((IrisStorageHistory) shaderStorageBufferHolder).portalix$restoreInitialContents();
    if (!customImages.isEmpty()) {
      GL43C.glMemoryBarrier(GL43C.GL_TEXTURE_UPDATE_BARRIER_BIT);
      for (var image : customImages) {
        if (!image.shouldClear()) IrisScene.clearImage(image);
      }
    }
  }

  @Inject(method = "beginLevelRendering", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
      target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;shadowRenderTargets:Lnet/irisshaders/iris/shadows/ShadowRenderTargets;",
      ordinal = 0), allow = 1)
  private void portalix$restoreShadowColors(CallbackInfo ci, @Local Vector4f emptyClearColor) {
    if (!portalix$resetViewHistory || shadowClearPassesFull.isEmpty()) return;
    // Reset both sides of each color target before setup or shadow compute can sample it.
    IrisRenderSystem.memoryBarrier(GL43C.GL_FRAMEBUFFER_BARRIER_BIT);
    for (var pass : shadowClearPassesFull) pass.execute(emptyClearColor);

    int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
    try {
      for (int index = 0; index < shadowRenderTargets.getRenderTargetCount(); index++) {
        var target = shadowRenderTargets.get(index);
        if (target == null) continue;
        portalix$refreshMipmaps(target.getMainTexture());
        portalix$refreshMipmaps(target.getAltTexture());
      }
    } finally {
      GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
    }
  }

  @Unique
  private static void portalix$refreshMipmaps(int texture) {
    GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
    if (GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 1, GL11C.GL_TEXTURE_WIDTH) != 0) {
      GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
    }
  }

  @ModifyExpressionValue(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
      target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;setup:[Lnet/irisshaders/iris/gl/program/ComputeProgram;"), allow = 1)
  private ComputeProgram[] portalix$deferPrewarmSetup(ComputeProgram[] programs) {
    // Compile and retain the programs. The first real view runs them after sizing its targets.
    return IrisScene.prewarming() ? portalix$noComputes : programs;
  }

  @ModifyExpressionValue(method = "beginLevelRendering",
      at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
          target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;shadowComputes:[Lnet/irisshaders/iris/gl/program/ComputeProgram;"), allow = 1)
  private ComputeProgram[] portalix$deferShadowComputes(ComputeProgram[] programs) {
    // Shadow compute can read storage and dispatch commands initialized by setup.
    if (!portalix$resetViewHistory || Arrays.stream(programs).noneMatch(Objects::nonNull)) return programs;
    portalix$deferredShadowClears = ImmutableList.of();
    return portalix$noComputes;
  }

  @ModifyVariable(method = "beginLevelRendering", at = @At("LOAD"), name = "passes",
      slice = @Slice(
          from = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target =
              "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;shadowComputes:[Lnet/irisshaders/iris/gl/program/ComputeProgram;"),
          to = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pbr/texture/PBRTextureManager;onNewFrame()V")), allow = 1)
  private ImmutableList<ClearPass> portalix$deferShadowClears(ImmutableList<ClearPass> passes) {
    if (portalix$deferredShadowClears == null) return passes;
    portalix$deferredShadowClears = passes;
    return ImmutableList.of();
  }

  @ModifyVariable(method = "beginLevelRendering", at = @At("LOAD"), name = "changed",
      slice = @Slice(from = @At(value = "INVOKE", target =
          "Lcom/mojang/blaze3d/pipeline/RenderTarget;iris$bindFramebuffer()V")), allow = 1)
  private boolean portalix$restoreSetup(boolean changed) {
    return ((IrisTargetHistory) renderTargets).portalix$consumeSetupReset() || changed;
  }

  @Inject(method = "beginLevelRendering", at = @At(value = "INVOKE",
      target = "Lnet/irisshaders/iris/pipeline/CompositeRenderer;renderAll()V"), allow = 1)
  private void portalix$finishHistoryReset(CallbackInfo ci) {
    portalix$resetViewHistory = false;
    var clears = portalix$deferredShadowClears;
    portalix$deferredShadowClears = null;
    if (clears == null) return;
    var customUniforms = ((IrisRenderingPipeline) (Object) this).getCustomUniforms();
    for (var program : shadowComputes) {
      if (program == null) continue;
      program.use();
      customUniforms.push(program);
      program.dispatch(shadowMapResolution, shadowMapResolution);
    }
    var white = new Vector4f(1);
    for (var clear : clears) clear.execute(white);
    ((RenderTargetInterface) IrisScene.mainTarget())
        .iris$bindFramebuffer();
  }
}

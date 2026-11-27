package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.portal.render.PortalLevelDraws;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.platform.ModPresence;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class PortalLevelRendererMixin {
  @Unique private static final boolean portalix$NEOFORGE = ModPresence.INSTANCE.isLoaded("neoforge");
  @Shadow @Final private LevelTargetBundle targets;
  @Unique private final PortalLevelDraws portalix$draws = new PortalLevelDraws();
  @Unique private RenderPass portalix$solidContinuation;
  @Unique private RenderPass portalix$weatherPass;

  @WrapMethod(method = "render")
  private void portalix$renderViews(
      GraphicsResourceAllocator resources,
      boolean outline,
      CameraRenderState camera,
      GpuBufferSlice fog,
      Vector4f fogColor,
      boolean sky,
      boolean consistentDepth,
      Operation<Void> original) {
    if (!PortalSceneManager.isPortalView())
      PortalSceneManager.render(Minecraft.getInstance().getDeltaTracker(), camera.viewRotationMatrix);
    var shaders = ShaderBackend.INSTANCE;
    portalix$draws.render(resources, () -> {
      Runnable render = () -> original.call(resources, outline, camera, fog, fogColor, sky, consistentDepth);
      if (shaders == null) render.run();
      else shaders.renderLevel(render);
    });
  }

  @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/LevelRenderer;executeSolid(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"), allow = 1)
  private void portalix$solidPassLifetime(
      LevelRenderer renderer, ChunkSectionsToRender terrain, PreparedFrame frame, RenderPass solid,
      Operation<Void> original,
      @Local(name = "renderPass") LocalRef<RenderPass> currentPass) {
    boolean completed = false;
    try {
      original.call(renderer, terrain, frame, solid);
      if (portalix$solidContinuation != null) currentPass.set(portalix$solidContinuation);
      completed = true;
    } finally {
      var continuation = portalix$solidContinuation;
      portalix$solidContinuation = null;
      if (!completed && continuation != null) continuation.close();
    }
  }

  @WrapOperation(method = "executeSolid", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"), allow = 1)
  private void portalix$finishSolidFeatures(
      PreparedFrame frame, RenderPass solid, Operation<Void> original,
      @Local(argsOnly = true) LocalRef<RenderPass> currentPass) {
    original.call(frame, solid);
    if (!PortalLevelDraws.hasSolid()) return;
    var gameRenderer = Minecraft.getInstance().gameRenderer;
    // Fabric can flush at the start of OIT. NeoForge posts AfterOpaqueFeatures with this pass.
    if (!portalix$NEOFORGE && gameRenderer.useImprovedTransparency()) return;

    // Portal surfaces may need a second attachment to record marker occlusion.
    solid.close();
    PortalLevelDraws.drawSolid();
    var target = gameRenderer.mainRenderTarget();
    portalix$solidContinuation = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
        () -> "After portal surfaces", target.getColorTextureView(), Optional.empty(),
        target.getDepthTextureView(), OptionalDouble.empty());
    RenderSystem.bindDefaultUniforms(portalix$solidContinuation);
    currentPass.set(portalix$solidContinuation);
  }

  @Inject(method = "executeOit", at = @At("HEAD"))
  private void portalix$beforeOit(CallbackInfo ci) {
    PortalLevelDraws.drawSolid();
    PortalLevelDraws.drawBeforeOit();
  }

  @WrapOperation(method = "executeOit", at = @At(value = "INVOKE",
      target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"), allow = 1)
  private RenderPass portalix$beforeOitComposite(
      CommandEncoder encoder, Supplier<String> name, GpuTextureView color, Optional<Vector4fc> clear,
      GpuTextureView depth, OptionalDouble clearDepth, Operation<RenderPass> original) {
    PortalLevelDraws.drawOitLight(targets);
    return original.call(encoder, name, color, clear, depth, clearDepth);
  }

  @Inject(method = "executeOit", at = @At("TAIL"))
  private void portalix$afterOitComposite(CallbackInfo ci) {
    PortalLevelDraws.drawOitRefraction(targets);
  }

  @WrapMethod(method = "executeClassicTransparency")
  private void portalix$classicTransparency(
      ChunkSectionsToRender terrain, PreparedFrame frame, RenderPass pass, Operation<Void> original) {
    try {
      original.call(terrain, frame, pass);
    } finally {
      var weatherPass = portalix$weatherPass;
      portalix$weatherPass = null;
      if (weatherPass != null) weatherPass.close();
    }
  }

  @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucentAfterTerrain(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"), allow = 1)
  private void portalix$classicAfterTerrain(
      PreparedFrame frame, RenderPass pass, Operation<Void> original,
      @Local(argsOnly = true) LocalRef<RenderPass> currentPass) {
    original.call(frame, pass);
    if (!PortalLevelDraws.hasAfterTerrain()) return;

    pass.close();
    PortalLevelDraws.drawAfterTerrain();
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    portalix$weatherPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
        () -> "Weather and clouds", target.getColorTextureView(), Optional.empty(),
        target.getDepthTextureView(), OptionalDouble.empty());
    RenderSystem.bindDefaultUniforms(portalix$weatherPass);
    currentPass.set(portalix$weatherPass);
  }

  @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/LevelRenderer;executeOutline(Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"))
  private void portalix$afterTerrain(CallbackInfo ci) {
    PortalLevelDraws.drawAfterTerrain();
  }
}

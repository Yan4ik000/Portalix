package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.compat.minecraft.SceneTransientBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.concurrent.BlockingQueue;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.TransientBlockRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class TransientBlockRendererMixin implements SceneTransientBlocks {
  @Shadow @Final private Long2ObjectLinkedOpenHashMap<TransientBlockRenderState> transientBlocks;
  @Shadow @Final private BlockingQueue<TransientBlockRenderState.Removal> transientBlockRemovalQueue;
  @Unique private @Nullable LevelRenderer portalix$transientBlockOwner;

  @Override
  public void portalix$transientBlockOwner(LevelRenderer owner) {
    portalix$transientBlockOwner = owner;
  }

  @Override
  public void portalix$clearTransientBlocks() {
    transientBlocks.clear();
    transientBlockRemovalQueue.clear();
  }

  @WrapOperation(method = "submitFeatures", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/LevelRenderer;submitTransientBlocks(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V"), allow = 1)
  private void portalix$submitSharedBlocks(
      LevelRenderer renderer, PoseStack poses, SubmitNodeCollector collector,
      LevelRenderState state, Operation<Void> original) {
    var owner = portalix$transientBlockOwner;
    // Views share published terrain, but submit using their own camera and feature storage.
    original.call(owner == null ? renderer : owner, poses, collector, state);
  }

  @ModifyExpressionValue(method = "performTransientBlockRemovals", at = @At(value = "FIELD", target =
      "Lnet/minecraft/client/Minecraft;levelRenderer:Lnet/minecraft/client/renderer/LevelRenderer;"),
      allow = 1)
  private LevelRenderer portalix$removeFromOwner(LevelRenderer activeRenderer) {
    return (LevelRenderer) (Object) this;
  }

  @Inject(method = "removeTransientBlocksInSection", at = @At("HEAD"), cancellable = true, allow = 1)
  private void portalix$forwardMeshPublication(long section, long compileTime, CallbackInfo ci) {
    var owner = portalix$transientBlockOwner;
    if (owner != null) {
      owner.removeTransientBlocksInSection(section, compileTime);
      ci.cancel();
    }
  }

  @WrapOperation(method = "addTransientBlock", at = @At(value = "INVOKE", target =
      "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;put(JLjava/lang/Object;)Ljava/lang/Object;"),
      allow = 1)
  private Object portalix$orderByCreation(
      Long2ObjectLinkedOpenHashMap<TransientBlockRenderState> blocks, long pos, Object state,
      Operation<Object> original) {
    // Vanilla stops scanning at the first state newer than the published mesh.
    return blocks.putAndMoveToLast(pos, (TransientBlockRenderState) state);
  }

  @Inject(method = {"resetLevelRenderData", "invalidateCompiledGeometry"}, at = @At("HEAD"), require = 2, allow = 2)
  private void portalix$clearTransientRenderState(CallbackInfo ci) {
    portalix$clearTransientBlocks();
  }
}

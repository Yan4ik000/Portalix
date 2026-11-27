package dev.portalix.client.mixin.feature;

import dev.portalix.client.render.PortalRenderQueue;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
abstract class PortalRenderQueueMixin implements PortalRenderQueue.Access {
  @Unique private final PortalRenderQueue portalix$queue = new PortalRenderQueue();

  @Override
  public PortalRenderQueue portalix$renderQueue() {
    return portalix$queue;
  }

  @Inject(method = "clear", at = @At("HEAD"))
  private void portalix$clearSubmissions(CallbackInfo ci) {
    portalix$queue.clear();
  }
}

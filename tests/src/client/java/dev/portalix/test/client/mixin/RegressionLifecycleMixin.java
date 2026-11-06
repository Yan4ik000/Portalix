package dev.portalix.test.client.mixin;

import dev.portalix.test.client.ClientRegressionRunner;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class RegressionLifecycleMixin {
  @Inject(method = "tick", at = @At("RETURN"))
  private void runRegression(CallbackInfo callback) {
    ClientRegressionRunner.tick((Minecraft) (Object) this);
  }
}

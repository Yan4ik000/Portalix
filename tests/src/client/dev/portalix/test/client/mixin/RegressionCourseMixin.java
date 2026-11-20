package dev.portalix.test.client.mixin;

import dev.portalix.test.client.ClientCourseServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class RegressionCourseMixin {
  @Inject(method = "createLevels", at = @At("RETURN"))
  private void prepareBeforeLogin(CallbackInfo callback) {
    ClientCourseServer.prepare((MinecraftServer) (Object) this);
  }
}

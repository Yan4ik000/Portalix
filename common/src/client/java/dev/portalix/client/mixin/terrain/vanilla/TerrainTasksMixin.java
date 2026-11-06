package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.terrain.SharedTerrainExecutor;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.function.Consumer;
import net.minecraft.TracingExecutor;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SectionRenderDispatcher.class)
abstract class TerrainTasksMixin {
  @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
  private static Consumer<SectionRenderDispatcher.RenderSection> portalix$worldPublication(
      Consumer<SectionRenderDispatcher.RenderSection> callback) {
    return PortalSceneManager::terrainChanged;
  }

  @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
  private static TracingExecutor portalix$terrainWorkers(
      TracingExecutor executor, @Local(argsOnly = true) RenderBuffers buffers) {

    return SharedTerrainExecutor.executor(buffers);
  }
}

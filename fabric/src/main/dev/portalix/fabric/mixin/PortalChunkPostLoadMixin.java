package dev.portalix.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.fabric.ChunkInitializationScope;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelChunk.class)
abstract class PortalChunkPostLoadMixin {
  @WrapMethod(method = "runPostLoad")
  private void portalix$initializingChunk(Operation<Void> original) {
    ChunkInitializationScope.run((LevelChunk) (Object) this, () -> original.call());
  }
}

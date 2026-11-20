package dev.portalix.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientChunkCache.class)
abstract class PortalChunkLoadEventMixin {
  @WrapOperation(method = "replaceWithPacketData", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;replace(ILnet/minecraft/world/level/chunk/LevelChunk;)V"))
  private void portalix$firstLoad(ClientChunkCache.Storage storage, int index, LevelChunk chunk,
      Operation<Void> original, @Share("firstLoad") LocalBooleanRef firstLoad) {
    original.call(storage, index, chunk);
    firstLoad.set(true);
  }

  @WrapOperation(method = "replaceWithPacketData", at = @At(value = "INVOKE", remap = false,
      target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
  private Event portalix$loadEvent(IEventBus bus, Event event,
      Operation<Event> original, @Share("firstLoad") LocalBooleanRef firstLoad) {
    return firstLoad.get() ? original.call(bus, event) : event;
  }
}

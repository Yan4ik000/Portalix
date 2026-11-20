package dev.portalix.test.neoforge;

import dev.portalix.test.ChunkAttachmentTestHooks;
import dev.portalix.test.ChunkWatchTestHooks;
import dev.portalix.test.PackagedModContract;
import dev.portalix.test.PlacementTestHooks;
import dev.portalix.test.RegressionGameTest;
import dev.portalix.test.ServerRegressionSuite;
import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.network.ConfigSync;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod("portalix_regression")
public final class RegressionMod {
  public RegressionMod(IEventBus bus) {
    PackagedModContract.verify();
    var attachments = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "portalix_regression");
    var types = new EnumMap<ChunkAttachmentTestHooks.Key, Supplier<AttachmentType<Integer>>>(ChunkAttachmentTestHooks.Key.class);
    for (var key : ChunkAttachmentTestHooks.Key.values())
      types.put(key, attachments.register(key.id().getPath(), () ->
          AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build()));
    attachments.register(bus);
    ChunkAttachmentTestHooks.register(new ChunkAttachmentTestHooks() {
      public Integer get(LevelChunk chunk, Key key) {
        return chunk.getExistingDataOrNull(types.get(key).get());
      }

      public void set(LevelChunk chunk, Key key, Integer value) {
        var type = types.get(key).get();
        if (value == null) chunk.removeData(type);
        else chunk.setData(type, value);
      }
    });
    NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
      if (!PlacementTestHooks.beforeBreak(event.getPlayer(), event.getPos())) event.setCanceled(true);
    });
    NeoForge.EVENT_BUS.addListener((BlockDropsEvent event) ->
        PlacementTestHooks.afterBreak(event.getBreaker(), event.getPos()));
    NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.Watch event) ->
        ChunkWatchTestHooks.record(event.getPlayer(), event.getLevel(), event.getPos(), ChunkWatchTestHooks.Event.WATCH));
    NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.Sent event) ->
        ChunkWatchTestHooks.record(event.getPlayer(), event.getLevel(), event.getPos(), ChunkWatchTestHooks.Event.SENT));
    NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.UnWatch event) ->
        ChunkWatchTestHooks.record(event.getPlayer(), event.getLevel(), event.getPos(), ChunkWatchTestHooks.Event.UNWATCH));
    var functions = DeferredRegister.<Consumer<GameTestHelper>>create(
        Registries.TEST_FUNCTION, "portalix_regression");
    for (var group : ServerRegressionSuite.Group.values()) {
      functions.register(group.id(), () -> helper ->
          RegressionGameTest.run(helper, group, listener -> {
            NetworkRegistry.configureMockConnection(listener.getConnection());
            ConfigSync.syncAllConfigs(listener);
          }));
    }
    functions.register(bus);
  }
}

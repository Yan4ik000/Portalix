package dev.portalix.test.fabric;

import dev.portalix.test.ChunkAttachmentTestHooks;
import dev.portalix.test.PackagedModContract;
import dev.portalix.test.PlacementTestHooks;
import dev.portalix.test.RegressionGameTest;
import dev.portalix.test.ServerRegressionSuite;
import java.util.EnumMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.LevelChunk;

public final class RegressionMod implements ModInitializer {
  @Override
  public void onInitialize() {
    PackagedModContract.verify();
    var attachments = new EnumMap<ChunkAttachmentTestHooks.Key, AttachmentType<Integer>>(ChunkAttachmentTestHooks.Key.class);
    for (var key : ChunkAttachmentTestHooks.Key.values())
      attachments.put(key, AttachmentRegistry.<Integer>builder()
          .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()).buildAndRegister(key.id()));
    ChunkAttachmentTestHooks.register(new ChunkAttachmentTestHooks() {
      public Integer get(LevelChunk chunk, Key key) {
        return ((AttachmentTarget) chunk).getAttached(attachments.get(key));
      }

      public void set(LevelChunk chunk, Key key, Integer value) {
        ((AttachmentTarget) chunk).setAttached(attachments.get(key), value);
      }
    });
    PlayerBlockBreakEvents.BEFORE.register((level, player, position, state, blockEntity) ->
        PlacementTestHooks.beforeBreak(player, position));
    PlayerBlockBreakEvents.AFTER.register((level, player, position, state, blockEntity) ->
        PlacementTestHooks.afterBreak(player, position));
    for (var group : ServerRegressionSuite.Group.values()) {
      Registry.register(BuiltInRegistries.TEST_FUNCTION,
          Identifier.fromNamespaceAndPath("portalix_regression", group.id()),
          helper -> RegressionGameTest.run(helper, group));
    }
  }
}

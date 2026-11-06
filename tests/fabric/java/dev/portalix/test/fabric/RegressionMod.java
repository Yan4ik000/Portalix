package dev.portalix.test.fabric;

import dev.portalix.test.RegressionGameTest;
import dev.portalix.test.PackagedModContract;
import dev.portalix.test.PlacementBreakVeto;
import dev.portalix.test.ServerRegressionSuite;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class RegressionMod implements ModInitializer {
  @Override
  public void onInitialize() {
    PackagedModContract.verify();
    PlayerBlockBreakEvents.BEFORE.register((level, player, position, state, blockEntity) ->
        PlacementBreakVeto.allow(player, position));
    for (var group : ServerRegressionSuite.Group.values()) {
      Registry.register(BuiltInRegistries.TEST_FUNCTION,
          Identifier.fromNamespaceAndPath("portalix_regression", group.id()),
          helper -> RegressionGameTest.run(helper, group));
    }
  }
}

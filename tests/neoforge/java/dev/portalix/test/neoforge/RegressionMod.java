package dev.portalix.test.neoforge;

import dev.portalix.test.RegressionGameTest;
import dev.portalix.test.PackagedModContract;
import dev.portalix.test.PlacementTestHooks;
import dev.portalix.test.ServerRegressionSuite;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@Mod("portalix_regression")
public final class RegressionMod {
  public RegressionMod(IEventBus bus) {
    PackagedModContract.verify();
    NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
      if (!PlacementTestHooks.beforeBreak(event.getPlayer(), event.getPos())) event.setCanceled(true);
    });
    NeoForge.EVENT_BUS.addListener((BlockDropsEvent event) ->
        PlacementTestHooks.afterBreak(event.getBreaker(), event.getPos()));
    var functions = DeferredRegister.<Consumer<GameTestHelper>>create(
        Registries.TEST_FUNCTION, "portalix_regression");
    for (var group : ServerRegressionSuite.Group.values()) {
      functions.register(group.id(), () -> helper ->
          RegressionGameTest.run(helper, group, NetworkRegistry::configureMockConnection));
    }
    functions.register(bus);
  }
}

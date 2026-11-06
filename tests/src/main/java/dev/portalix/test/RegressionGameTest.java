package dev.portalix.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import dev.portalix.test.mixin.GameTestHelperAccessor;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.slf4j.Logger;

public final class RegressionGameTest {
  private static final Logger LOGGER = LogUtils.getLogger();

  private RegressionGameTest() {}

  public static void run(GameTestHelper helper, ServerRegressionSuite.Group group) {
    run(helper, group, connection -> {});
  }

  public static void run(GameTestHelper helper, ServerRegressionSuite.Group group,
      Consumer<Connection> configureConnection) {
    var level = helper.getLevel();
    if (!level.getServer().isSameThread())
      throw new IllegalStateException("GameTest fixtures must execute synchronously on the server thread");
    var profile = new GameProfile(UUID.randomUUID(), "PortalixTest");
    var cookie = CommonListenerCookie.createInitial(profile, false);
    // Native GameTest players are subclasses; NeoForge excludes them from configuration handshake state.
    var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation()) {};
    var connection = new Connection(PacketFlow.SERVERBOUND);
    var channel = new EmbeddedChannel(connection);
    var fixture = new Fixture(player, channel);
    ((GameTestHelperAccessor) helper).portalix$testInfo().addListener(new GameTestListener() {
      public void testStructureLoaded(GameTestInfo info) {}
      public void testPassed(GameTestInfo info, GameTestRunner runner) { fixture.close(); }
      public void testFailed(GameTestInfo info, GameTestRunner runner) {
        fixture.close();
      }
      public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) { fixture.close(); }
    });
    try {
      configureConnection.accept(connection);
      level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
      helper.onEachTick(() -> {
        if (fixture.closed) return;
        try {
          if (fixture.world == null) fixture.world = new RegressionWorld(level, player, channel);
          if (!fixture.started) {
            if (!fixture.world.ready()) return;
            fixture.scenario = ServerRegressionSuite.start(fixture.world, group);
            fixture.started = true;
          }
          if (fixture.scenario != null && !fixture.scenario.tick()) return;
          fixture.close();
          passed(helper, group);
        } catch (RuntimeException | AssertionError failure) {
          failed(helper, group, fixture, failure);
        }
      });
    } catch (RuntimeException | AssertionError failure) {
      failed(helper, group, fixture, failure);
      return;
    }
  }

  private static void passed(GameTestHelper helper, ServerRegressionSuite.Group group) {
    LOGGER.info("PORTALIX SERVER REGRESSION PASS: {}: {}", group.id(), group.description());
    helper.succeed();
  }

  private static void failed(GameTestHelper helper, ServerRegressionSuite.Group group, Fixture fixture, Throwable failure) {
    try { fixture.close(); }
    catch (RuntimeException | AssertionError cleanup) { failure.addSuppressed(cleanup); }
    LOGGER.error("Server regression {} failed", group.id(), failure);
    helper.fail(group.id() + ": " + failure.getMessage());
  }

  private static final class Fixture implements AutoCloseable {
    private final ServerPlayer player;
    private final EmbeddedChannel channel;
    private ServerRegressionSuite.Scenario scenario;
    private boolean started;
    private RegressionWorld world;
    private boolean closed;

    private Fixture(ServerPlayer player, EmbeddedChannel channel) {
      this.player = player;
      this.channel = channel;
    }

    public void close() {
      if (closed) return;
      closed = true;
      try {
        if (scenario != null) scenario.close();
      }
      finally {
        try {
          if (world != null) world.close();
        } finally {
          try {
            var players = player.level().getServer().getPlayerList();
            if (players.getPlayer(player.getUUID()) == player) players.remove(player);
          } finally {
            try {
              if (player.connection != null) player.connection.resumeFlushing();
            } finally {
              channel.finishAndReleaseAll();
            }
          }
        }
      }
    }
  }

}

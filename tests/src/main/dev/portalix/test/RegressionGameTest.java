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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;

public final class RegressionGameTest {
  private static final Logger LOGGER = LogUtils.getLogger();

  private RegressionGameTest() {}

  public static void run(GameTestHelper helper, ServerRegressionSuite.Group group) {
    run(helper, group, connection -> {});
  }

  public static void run(GameTestHelper helper, ServerRegressionSuite.Group group,
      Consumer<ServerConfigurationPacketListenerImpl> configureConnection) {
    var level = helper.getLevel();
    if (!level.getServer().isSameThread())
      throw new IllegalStateException("GameTest fixtures must execute synchronously on the server thread");
    var participant = new Participant(level, "PortalixTest");
    var fixture = new Fixture(participant);
    ((GameTestHelperAccessor) helper).portalix$testInfo().addListener(new GameTestListener() {
      public void testStructureLoaded(GameTestInfo info) {}
      public void testPassed(GameTestInfo info, GameTestRunner runner) { fixture.close(); }
      public void testFailed(GameTestInfo info, GameTestRunner runner) { fixture.close(); }
      public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) { fixture.close(); }
    });
    try {
      participant.connect(configureConnection);
      helper.onEachTick(() -> {
        if (fixture.closed) return;
        try {
          if (fixture.world == null) fixture.world = new RegressionWorld(level, participant.player, participant.channel, configureConnection);
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

  static final class Participant implements AutoCloseable {
    ServerPlayer player;
    final Connection connection = new Connection(PacketFlow.SERVERBOUND);
    final EmbeddedChannel channel = new EmbeddedChannel(connection);
    private final CommonListenerCookie cookie;
    private boolean closed;

    Participant(ServerLevel level, String name) {
      var profile = new GameProfile(UUID.randomUUID(), name);
      cookie = CommonListenerCookie.createInitial(profile, false);
      player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
    }

    void connect(Consumer<ServerConfigurationPacketListenerImpl> configure) {
      configure.accept(new ServerConfigurationPacketListenerImpl(player.level().getServer(), connection, cookie));
      player.level().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
    }

    public void close() {
      if (closed) return;
      closed = true;
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

  private static final class Fixture implements AutoCloseable {
    private final Participant participant;
    private ServerRegressionSuite.Scenario scenario;
    private boolean started;
    private RegressionWorld world;
    private boolean closed;

    private Fixture(Participant participant) {
      this.participant = participant;
    }

    public void close() {
      if (closed) return;
      closed = true;
      try {
        if (scenario != null) scenario.close();
      } finally {
        try {
          if (world != null) world.close();
        } finally {
          participant.close();
        }
      }
    }
  }
}

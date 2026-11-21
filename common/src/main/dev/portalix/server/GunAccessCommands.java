package dev.portalix.server;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import org.slf4j.Logger;

final class GunAccessCommands {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final SimpleCommandExceptionType UNAVAILABLE = new SimpleCommandExceptionType(
      Component.translatableWithFallback("commands.portalix.access.unavailable", "Portalix access settings are unavailable."));
  private static final SimpleCommandExceptionType SAVE_FAILED = new SimpleCommandExceptionType(
      Component.translatableWithFallback("commands.portalix.access.save_failed", "Could not save Portalix access settings. See the server log."));
  private static final SimpleCommandExceptionType NOT_TRUSTED = new SimpleCommandExceptionType(
      Component.translatableWithFallback("commands.portalix.access.not_trusted", "That player is not on the Portalix allowlist."));
  private static final SimpleCommandExceptionType AMBIGUOUS_PLAYER = new SimpleCommandExceptionType(
      Component.translatableWithFallback("commands.portalix.access.ambiguous", "Multiple allowlist entries have that name. Use the player's UUID."));

  private GunAccessCommands() {}

  static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(literal("portalix")
        .requires(GunAccessCommands::canManage)
        .then(literal("access")
            .executes(context -> showMode(context.getSource()))
            .then(literal("mode")
                .executes(context -> showMode(context.getSource()))
                .then(literal("all").executes(context -> setMode(context.getSource(), false)))
                .then(literal("restricted").executes(context -> setMode(context.getSource(), true))))
            .then(literal("list").executes(context -> list(context.getSource())))
            .then(literal("add")
                .then(argument("players", GameProfileArgument.gameProfile())
                    .executes(context -> add(context.getSource(), GameProfileArgument.getGameProfiles(context, "players")))))
            .then(literal("remove")
                .then(argument("player", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        access(context.getSource()).trusted().values(), builder))
                    .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "player")))))));
  }

  private static boolean canManage(CommandSourceStack source) {
    var player = source.getPlayer();
    return player != null
        ? source.getServer().getPlayerList().isOp(player.nameAndId())
        : Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_ADMINS).test(source);
  }

  private static GunAccess access(CommandSourceStack source) throws CommandSyntaxException {
    var access = PortalServerEvents.gunAccess(source.getServer());
    if (access == null) throw UNAVAILABLE.create();
    return access;
  }

  private static int showMode(CommandSourceStack source) throws CommandSyntaxException {
    var mode = access(source).restricted()
        ? Component.translatableWithFallback("commands.portalix.access.mode.restricted", "Only operators and allowlisted players can use the Portalix gun.")
        : Component.translatableWithFallback("commands.portalix.access.mode.all", "All players can use the Portalix gun.");
    source.sendSuccess(() -> mode, false);
    return 1;
  }

  private static int setMode(CommandSourceStack source, boolean restricted) throws CommandSyntaxException {
    try {
      if (access(source).setRestricted(restricted)) PortalServerEvents.gunAccessChanged(source.getServer());
    } catch (IOException failure) {
      throw saveFailure(failure);
    }
    return showMode(source);
  }

  private static int list(CommandSourceStack source) throws CommandSyntaxException {
    var players = access(source).trusted().entrySet().stream()
        .map(entry -> entry.getValue() + " (" + entry.getKey() + ")")
        .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    source.sendSuccess(() -> players.isEmpty()
        ? Component.translatableWithFallback("commands.portalix.access.empty", "The Portalix allowlist is empty.")
        : Component.translatableWithFallback("commands.portalix.access.list", "Portalix allowlist (%s): %s",
            players.size(), String.join(", ", players)), false);
    return players.size();
  }

  private static int add(CommandSourceStack source, Collection<NameAndId> players) throws CommandSyntaxException {
    final int changed;
    try {
      changed = access(source).addTrusted(players);
    } catch (IOException failure) {
      throw saveFailure(failure);
    }
    source.sendSuccess(() -> Component.translatableWithFallback(
        "commands.portalix.access.added", "Portalix allowlist entries updated: %s", changed), false);
    return changed;
  }

  private static int remove(CommandSourceStack source, String player) throws CommandSyntaxException {
    var access = access(source);
    var matches = access.trusted().entrySet().stream()
        .filter(entry -> entry.getKey().toString().equalsIgnoreCase(player) || entry.getValue().equalsIgnoreCase(player))
        .map(Map.Entry::getKey).toList();
    if (matches.isEmpty()) throw NOT_TRUSTED.create();
    if (matches.size() != 1) throw AMBIGUOUS_PLAYER.create();
    UUID id = matches.getFirst();
    String name = access.trusted().get(id);
    try {
      access.removeTrusted(id);
    } catch (IOException failure) {
      throw saveFailure(failure);
    }
    PortalServerEvents.gunAccessChanged(source.getServer());
    source.sendSuccess(() -> Component.translatableWithFallback(
        "commands.portalix.access.removed", "Removed %s from the Portalix allowlist.", name), false);
    return 1;
  }

  private static CommandSyntaxException saveFailure(IOException failure) {
    LOGGER.error("Failed to save Portalix gun access settings", failure);
    return SAVE_FAILED.create();
  }
}

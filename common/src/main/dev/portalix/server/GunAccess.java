package dev.portalix.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

final class GunAccess {
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

  private record Rules(boolean restricted, Map<UUID, String> trusted) {
    private static final Codec<Rules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("restricted").forGetter(Rules::restricted),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
            .optionalFieldOf("trusted", Map.of()).forGetter(Rules::trusted)
    ).apply(instance, Rules::new));

    Rules {
      trusted = Collections.unmodifiableMap(new TreeMap<>(trusted));
    }
  }

  private final Path file;
  private Rules rules;

  GunAccess(Path worldDirectory) {
    file = worldDirectory.resolve("portalix-access.json");
    rules = read(file);
  }

  boolean allows(ServerPlayer player) {
    return !rules.restricted() || rules.trusted().containsKey(player.getUUID())
        || player.level().getServer().getPlayerList().isOp(player.nameAndId());
  }

  boolean restricted() {
    return rules.restricted();
  }

  Map<UUID, String> trusted() {
    return rules.trusted();
  }

  boolean setRestricted(boolean restricted) throws IOException {
    if (restricted == rules.restricted()) return false;
    save(new Rules(restricted, rules.trusted()));
    return true;
  }

  int addTrusted(Collection<NameAndId> profiles) throws IOException {
    var trusted = new TreeMap<>(rules.trusted());
    for (var profile : profiles) trusted.put(profile.id(), profile.name());
    if (trusted.equals(rules.trusted())) return 0;
    int changed = (int) trusted.entrySet().stream()
        .filter(entry -> !entry.getValue().equals(rules.trusted().get(entry.getKey()))).count();
    save(new Rules(rules.restricted(), trusted));
    return changed;
  }

  void removeTrusted(UUID player) throws IOException {
    var trusted = new TreeMap<>(rules.trusted());
    trusted.remove(player);
    save(new Rules(rules.restricted(), trusted));
  }

  private static Rules read(Path file) {
    try (var reader = Files.newBufferedReader(file)) {
      return Rules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
    } catch (NoSuchFileException missing) {
      return new Rules(false, Map.of());
    } catch (IOException | IllegalStateException | JsonParseException failure) {
      throw new IllegalStateException("Could not load Portalix gun access from " + file, failure);
    }
  }

  private void save(Rules next) throws IOException {
    var document = Rules.CODEC.encodeStart(JsonOps.INSTANCE, next).getOrThrow();
    var temporary = Files.createTempFile(file.getParent(), "portalix-access-", ".tmp");
    try {
      Files.writeString(temporary, JSON.toJson(document) + "\n");
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
    rules = next;
  }
}

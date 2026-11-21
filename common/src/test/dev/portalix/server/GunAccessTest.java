package dev.portalix.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.players.NameAndId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GunAccessTest {
  @TempDir Path world;

  @Test
  void preservesAllowlistAcrossModeChangesAndWorldReopens() throws IOException {
    var player = new NameAndId(UUID.fromString("5af256b8-8df3-4e7f-9369-17573b6ca3c9"), "TrustedPlayer");
    var access = new GunAccess(world);
    assertFalse(access.restricted());
    assertEquals(1, access.addTrusted(List.of(player)));
    assertTrue(access.setRestricted(true));

    var reopened = new GunAccess(world);
    assertTrue(reopened.restricted());
    assertEquals(Map.of(player.id(), player.name()), reopened.trusted());
    assertTrue(reopened.setRestricted(false));

    var unrestricted = new GunAccess(world);
    assertFalse(unrestricted.restricted());
    assertEquals(Map.of(player.id(), player.name()), unrestricted.trusted());
    unrestricted.removeTrusted(player.id());
    assertTrue(new GunAccess(world).trusted().isEmpty());
  }

  @Test
  void keepsCurrentAccessWhenSavingFails() throws IOException {
    var access = new GunAccess(world);
    Files.createDirectory(world.resolve("portalix-access.json"));

    assertThrows(IOException.class, () -> access.setRestricted(true));
    assertFalse(access.restricted());
    try (var paths = Files.list(world)) {
      assertEquals(List.of("portalix-access.json"), paths.map(Path::getFileName).map(Path::toString).toList());
    }
  }
}

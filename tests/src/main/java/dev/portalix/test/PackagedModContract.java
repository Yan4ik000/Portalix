package dev.portalix.test;

import dev.portalix.PortalixMod;
import dev.portalix.platform.PortalPlatform;
import dev.portalix.platform.RegistryPlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PackagedModContract {
  private PackagedModContract() {}

  public static void verify() {
    String selected = System.getProperty("portalix.test.packagedArchive");
    if (selected == null) return;
    try {
      Path archive = Path.of(selected).toRealPath();
      if (!Files.isRegularFile(archive) || !archive.getFileName().toString().endsWith(".jar"))
        throw new AssertionError("Packaged regression requires the actual mod JAR: " + archive);
      for (Class<?> type : new Class<?>[] {
          PortalixMod.class, PortalPlatform.INSTANCE.getClass(), RegistryPlatform.INSTANCE.getClass()}) {
        var location = type.getProtectionDomain().getCodeSource().getLocation();
        if (!Path.of(location.toURI()).toRealPath().equals(archive))
          throw new AssertionError(type.getName() + " loaded from " + location + " instead of " + archive);
      }
      var digest = MessageDigest.getInstance("SHA-256");
      try (var stream = Files.newInputStream(archive)) {
        byte[] bytes = new byte[65536];
        int count;
        while ((count = stream.read(bytes)) != -1) digest.update(bytes, 0, count);
      }
      System.out.println("PORTALIX PACKAGED MOD VERIFIED: " + archive + " SHA256="
          + HexFormat.of().formatHex(digest.digest()));
    } catch (IOException | java.net.URISyntaxException | NoSuchAlgorithmException failure) {
      throw new AssertionError("Cannot verify the packaged mod origin", failure);
    }
  }
}

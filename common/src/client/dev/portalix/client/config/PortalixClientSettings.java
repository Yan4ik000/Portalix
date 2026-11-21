package dev.portalix.client.config;

import static dev.portalix.portal.PortalViewLimits.DEFAULT_PREVIEW_RADIUS;
import static dev.portalix.portal.PortalViewLimits.MAX_PREVIEW_RADIUS;
import static dev.portalix.portal.PortalViewLimits.MIN_PREVIEW_RADIUS;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;

public record PortalixClientSettings(
    boolean showPortalCrosshair,
    int ownPortalRenderDistance,
    int otherPortalRenderDistance,
    boolean portalAmbientSound) {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final PortalixClientSettings DEFAULTS =
      new PortalixClientSettings(true, DEFAULT_PREVIEW_RADIUS, DEFAULT_PREVIEW_RADIUS, true);
  private static final Codec<PortalixClientSettings> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
          Codec.BOOL.optionalFieldOf("showPortalCrosshair", DEFAULTS.showPortalCrosshair)
              .forGetter(PortalixClientSettings::showPortalCrosshair),
          Codec.INT.optionalFieldOf("ownPortalRenderDistance", DEFAULT_PREVIEW_RADIUS)
              .forGetter(PortalixClientSettings::ownPortalRenderDistance),
          Codec.INT.optionalFieldOf("otherPortalRenderDistance", DEFAULT_PREVIEW_RADIUS)
              .forGetter(PortalixClientSettings::otherPortalRenderDistance),
          Codec.BOOL.optionalFieldOf("portalAmbientSound", DEFAULTS.portalAmbientSound)
              .forGetter(PortalixClientSettings::portalAmbientSound))
          .apply(instance, PortalixClientSettings::new));
  private static volatile PortalixClientSettings current = DEFAULTS;
  private static Path file;

  public PortalixClientSettings {
    ownPortalRenderDistance = Math.clamp(ownPortalRenderDistance, MIN_PREVIEW_RADIUS, MAX_PREVIEW_RADIUS);
    otherPortalRenderDistance = Math.clamp(otherPortalRenderDistance, MIN_PREVIEW_RADIUS, MAX_PREVIEW_RADIUS);
  }

  public static PortalixClientSettings get() {
    return current;
  }

  public static void load(Path configDirectory) {
    file = configDirectory.resolve("portalix-client.json");
    current = DEFAULTS;
    try (var reader = Files.newBufferedReader(file)) {
      current = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow(JsonParseException::new);
    } catch (NoSuchFileException ignored) {
      try {
        write(DEFAULTS);
      } catch (IOException e) {
        LOGGER.warn("Could not create Portalix client settings at {}", file, e);
      }
    } catch (IOException | JsonParseException e) {
      LOGGER.warn("Could not read Portalix client settings at {}", file, e);
    }
  }

  static boolean save(PortalixClientSettings settings) {
    if (settings.equals(current)) return true;
    try {
      write(settings);
      current = settings;
      return true;
    } catch (IOException e) {
      LOGGER.error("Could not save Portalix client settings at {}", file, e);
      return false;
    }
  }

  private static void write(PortalixClientSettings settings) throws IOException {
    Files.createDirectories(file.getParent());
    var pending = Files.createTempFile(file.getParent(), "portalix-client-", ".tmp");
    try {
      Files.writeString(pending, GSON.toJson(settings) + "\n");
      try {
        Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(pending);
    }
  }
}

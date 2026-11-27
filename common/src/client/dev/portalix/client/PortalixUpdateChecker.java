package dev.portalix.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import dev.portalix.client.compat.minecraft.GuiAccess;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public final class PortalixUpdateChecker {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final String RELEASES_API = "https://api.github.com/repos/Yan4ik000/Portalix/releases";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private PortalixUpdateChecker() {}

  public static void check(Minecraft client, Connection connection, String loader, String installedVersion) {
    Version current = Version.parse(installedVersion);
    if (current == null) return;

    String artifactPrefix = "Portalix-" + loader + "-" + SharedConstants.getCurrentVersion().id() + "-";
    Util.ioPool().execute(() -> {
      try {
        Version latest = latestRelease(artifactPrefix, current);
        if (latest.equals(current)) return;

        client.execute(() -> {
          var listener = client.getConnection();
          if (listener != null && listener.getConnection() == connection && connection.isConnected()) {
            GuiAccess.addChatMessage(client, Component.translatable(
                "message.portalix.update.available", "v" + current, "v" + latest));
          }
        });
      } catch (IOException | JsonParseException e) {
        LOGGER.debug("Could not check GitHub for Portalix updates", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
  }

  private static Version latestRelease(String artifactPrefix, Version current)
      throws IOException, InterruptedException {
    try (HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
      Version latest = current;
      for (int page = 1; ; page++) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API + "?per_page=100&page=" + page))
            .timeout(TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Portalix")
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("GitHub returned HTTP " + response.statusCode());

        latest = latestCompatibleVersion(response.body(), artifactPrefix, latest);
        if (response.headers().allValues("Link").stream().noneMatch(link -> link.contains("rel=\"next\""))) {
          return latest;
        }
      }
    }
  }

  static Version latestCompatibleVersion(String json, String artifactPrefix, Version latest) {
    Release[] releases = GSON.fromJson(json, Release[].class);
    if (releases == null) throw new JsonParseException("Missing GitHub releases array");

    for (Release release : releases) {
      if (release == null || release.draft || release.prerelease || release.assets == null) continue;

      Version version = Version.parse(release.tag);
      if (version == null || version.compareTo(latest) <= 0) continue;

      String jar = artifactPrefix + version + ".jar";
      for (Asset asset : release.assets) {
        if (asset != null && jar.equals(asset.name) && "uploaded".equals(asset.state)) {
          latest = version;
          break;
        }
      }
    }
    return latest;
  }

  private record Release(@SerializedName("tag_name") String tag, boolean draft, boolean prerelease, Asset[] assets) {}

  private record Asset(String name, String state) {}

  record Version(int major, int minor, int patch) implements Comparable<Version> {
    private static final String PART = "(0|[1-9][0-9]*)";
    private static final Pattern FORMAT = Pattern.compile("(?:v\\.?)?" + PART + "\\." + PART + "\\." + PART);
    private static final Comparator<Version> ORDER = Comparator.comparingInt(Version::major)
        .thenComparingInt(Version::minor)
        .thenComparingInt(Version::patch);

    static Version parse(String value) {
      if (value == null) return null;
      var matcher = FORMAT.matcher(value);
      if (!matcher.matches()) return null;
      try {
        return new Version(
            Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
      } catch (NumberFormatException ignored) {
        return null;
      }
    }

    @Override
    public int compareTo(Version other) {
      return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
      return major + "." + minor + "." + patch;
    }
  }
}

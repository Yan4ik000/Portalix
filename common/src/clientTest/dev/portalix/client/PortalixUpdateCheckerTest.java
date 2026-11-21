package dev.portalix.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.portalix.client.PortalixUpdateChecker.Version;
import org.junit.jupiter.api.Test;

class PortalixUpdateCheckerTest {
  @Test
  void selectsHighestPublishedVersionWithMatchingUploadedJar() {
    String releases = """
        [
          {"tag_name":"v1.10.0","assets":[{"name":"Portalix-fabric-26.3-1.10.0.jar","state":"uploaded"}]},
          {"tag_name":"v1.9.0","assets":[{"name":"Portalix-fabric-26.3-1.9.0.jar","state":"uploaded"}]},
          {"tag_name":"v2.0.0","assets":[{"name":"Portalix-fabric-26.30-2.0.0.jar","state":"uploaded"}]},
          {"tag_name":"v2.1.0","assets":[{"name":"Portalix-neoforge-26.3-2.1.0.jar","state":"uploaded"}]},
          {"tag_name":"v2.2.0","assets":[{"name":"Portalix-fabric-26.3-2.2.0-sources.jar","state":"uploaded"}]},
          {"tag_name":"v2.3.0","assets":[{"name":"Portalix-fabric-26.3-2.2.0.jar","state":"uploaded"}]},
          {"tag_name":"v2.4.0","assets":[{"name":"Portalix-fabric-26.3-2.4.0.jar","state":"starter"}]},
          {"tag_name":"v3.0.0","prerelease":true,
            "assets":[{"name":"Portalix-fabric-26.3-3.0.0.jar","state":"uploaded"}]},
          {"tag_name":"v4.0.0","draft":true,
            "assets":[{"name":"Portalix-fabric-26.3-4.0.0.jar","state":"uploaded"}]}
        ]
        """;
    assertEquals(Version.parse("1.10.0"),
        PortalixUpdateChecker.latestCompatibleVersion(releases, "Portalix-fabric-26.3-", Version.parse("1.2.0")));
  }

  @Test
  void acceptsLegacyTagSpellingAndNeverSelectsAnOlderVersion() {
    String releases = """
        [{"tag_name":"v.1.1.0","assets":[{"name":"Portalix-neoforge-26.2-1.1.0.jar","state":"uploaded"}]}]
        """;
    assertEquals(Version.parse("1.1.0"),
        PortalixUpdateChecker.latestCompatibleVersion(releases, "Portalix-neoforge-26.2-", Version.parse("1.0.0")));
    assertEquals(Version.parse("1.2.0"),
        PortalixUpdateChecker.latestCompatibleVersion(releases, "Portalix-neoforge-26.2-", Version.parse("1.2.0")));
  }
}

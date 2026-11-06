package dev.portalix.portal;

import dev.portalix.api.PortalInstance;
import dev.portalix.portal.geometry.PortalTransform;
import java.util.Objects;
import java.util.UUID;

public record PortalLink(UUID gun, long generation, PortalInstance entrance, PortalInstance exit) {
  public PortalLink {
    Objects.requireNonNull(gun);
    Objects.requireNonNull(entrance);
    Objects.requireNonNull(exit);
  }

  public PortalTransform transform() {
    return new PortalTransform(entrance.endpoint(), exit.endpoint());
  }

  public PortalLink inverse() {
    return new PortalLink(gun, generation, exit, entrance);
  }
}

package dev.portalix.portal;

import java.util.List;
import java.util.UUID;

public record PortalLinkId(UUID gun, long generation, long entrance, long exit) {
  public PortalLinkId inverse() {
    return new PortalLinkId(gun, generation, exit, entrance);
  }

  public static PortalLinkId fromLink(PortalLink link) {
    return new PortalLinkId(
        link.gun(), link.generation(), link.entrance().identity(), link.exit().identity());
  }

  public PortalLink resolve(List<PortalLink> links) {
    for (PortalLink link : links)
      if (generation == link.generation()
          && entrance == link.entrance().identity()
          && exit == link.exit().identity()
          && gun.equals(link.gun())) return link;
    return null;
  }
}

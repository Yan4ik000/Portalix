package dev.portalix.api;

import dev.portalix.item.PortalixVariant;

public record PortalInstance(
    PortalEndpoint endpoint, long bornTick, long identity, PortalixVariant variant) {}

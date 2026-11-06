package dev.portalix.test.client.mixin;

import dev.portalix.server.PortalServerEvents;
import dev.portalix.server.PortalServerSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PortalServerEvents.class, remap = false)
public interface PortalServerEventsAccessor {
  @Accessor("session")
  static PortalServerSession portalix$session() {
    throw new AssertionError();
  }
}

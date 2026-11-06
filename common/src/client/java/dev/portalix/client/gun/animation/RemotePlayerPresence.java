package dev.portalix.client.gun.animation;

import java.util.Objects;
import java.util.UUID;

final class RemotePlayerPresence {
  static final int SPAWN_GRACE_TICKS = 100;
  static final int UNTRACK_GRACE_TICKS = 5;
  private final UUID expectedOwner;
  private int missingTicks;
  private boolean seen;

  RemotePlayerPresence(UUID expectedOwner) {
    this.expectedOwner = Objects.requireNonNull(expectedOwner, "Snapshot owner UUID");
  }

  boolean expects(UUID owner) {
    return this.expectedOwner.equals(owner);
  }

  boolean pendingSpawn() {
    return !this.seen;
  }

  void received() {
    this.missingTicks = 0;
  }

  boolean expired(UUID actualOwner) {
    if (this.expects(actualOwner)) {
      this.seen = true;
      this.missingTicks = 0;
      return false;
    } else {
      return ++this.missingTicks > (this.seen ? UNTRACK_GRACE_TICKS : SPAWN_GRACE_TICKS);
    }
  }
}

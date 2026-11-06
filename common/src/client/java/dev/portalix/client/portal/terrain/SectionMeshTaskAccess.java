package dev.portalix.client.portal.terrain;

import java.util.concurrent.atomic.AtomicBoolean;

public interface SectionMeshTaskAccess {
  void portalix$task(AtomicBoolean cancelled);

  boolean portalix$cancelled();
}

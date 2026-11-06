package dev.portalix.client.portal.terrain;

import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.SectionUpdateTracker;

public interface SectionUpdateStorageAccess {
  RotatingSectionStorage<SectionUpdateTracker.SectionDirtyState> portalix$storage();
}

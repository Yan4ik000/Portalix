package dev.portalix.client.mixin.terrain;

import dev.portalix.client.portal.terrain.SectionUpdateStorageAccess;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.SectionUpdateTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionUpdateTracker.class)
public interface SectionUpdateTrackerAccessor extends SectionUpdateStorageAccess {
  @Override
  @Accessor("storage")
  RotatingSectionStorage<SectionUpdateTracker.SectionDirtyState> portalix$storage();
}

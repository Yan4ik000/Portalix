package dev.portalix.client.mixin.terrain;

import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ViewArea.class)
public interface ViewAreaAccessor {
  @Accessor("sections")
  RotatingSectionStorage<SectionRenderDispatcher.RenderSection> portalix$sections();

  @Mutable
  @Accessor("sections")
  void portalix$sections(RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections);
}

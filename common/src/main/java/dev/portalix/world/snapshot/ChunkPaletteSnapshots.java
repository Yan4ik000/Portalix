package dev.portalix.world.snapshot;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public interface ChunkPaletteSnapshots {
  @Nullable List<PaletteSnapshot<BlockState>> portalix$palettes();
}

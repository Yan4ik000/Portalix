package dev.portalix.world.snapshot;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DuplicatedByteBuf;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

@SuppressWarnings("deprecation")
public final class LocalPaletteBuffer extends DuplicatedByteBuf {
  private final List<PaletteSnapshot<BlockState>> palettes;

  public LocalPaletteBuffer(ByteBuf buffer, List<PaletteSnapshot<BlockState>> palettes) {
    super(buffer);
    this.palettes = palettes;
  }

  public List<PaletteSnapshot<BlockState>> palettes() {
    return palettes;
  }
}

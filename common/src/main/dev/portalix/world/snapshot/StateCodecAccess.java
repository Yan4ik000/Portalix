package dev.portalix.world.snapshot;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.StateDefinition;

public interface StateCodecAccess {
  Entry portalix$stateCodec();

  void portalix$stateCodec(Entry codec);

  record Entry(StateDefinition<?, ?> definition, MapCodec<?> codec) {}
}

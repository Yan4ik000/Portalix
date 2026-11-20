package dev.portalix.mixin.world.snapshot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.portalix.world.snapshot.StateCodecAccess;
import java.util.function.Function;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StateHolder.class)
abstract class PortalStateCodecMixin implements StateCodecAccess {
  @Unique private volatile Entry portalix$codec;

  @Override
  public Entry portalix$stateCodec() {
    return portalix$codec;
  }

  @Override
  public void portalix$stateCodec(Entry codec) {
    portalix$codec = codec;
  }

  @WrapOperation(
      method = "codec",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/serialization/Codec;dispatch(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
  private static Codec<?> portalix$reuseCodec(
      Codec<?> ownerCodec,
      String key,
      Function<?, ?> ownerGetter,
      Function<Object, MapCodec<?>> stateCodec,
      Operation<Codec<?>> original,
      Codec<?> inputCodec,
      Function<Object, StateHolder<?, ?>> defaults,
      Function<Object, StateDefinition<?, ?>> definitions) {
    Function<Object, MapCodec<?>> cachedCodec =
        owner -> {
          var definition = definitions.apply(owner);
          var state = (StateCodecAccess) defaults.apply(owner);
          var cached = state.portalix$stateCodec();
          if (cached != null && cached.definition() == definition) return cached.codec();
          var codec = stateCodec.apply(owner);
          state.portalix$stateCodec(new Entry(definition, codec));
          return codec;
        };
    return original.call(ownerCodec, key, ownerGetter, cachedCodec);
  }
}

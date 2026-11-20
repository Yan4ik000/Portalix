package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import java.util.function.Predicate;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = ClonedChunkSectionCache.class, remap = false)
abstract class SodiumSnapshotCacheMixin {
  @Shadow @Final private Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> positionToEntry;

  @Definition(
      id = "size",
      method = "Lit/unimi/dsi/fastutil/longs/Long2ReferenceLinkedOpenHashMap;size()I")
  @Expression("?.size() >= ?")
  @ModifyExpressionValue(method = "acquire", at = @At("MIXINEXTRAS:EXPRESSION"), allow = 1)
  private boolean portalix$retainWorkingSet(boolean overCapacity) {
    // Views share the cache; invalidation, unloading and expiry retire their snapshots.
    return false;
  }

  @WrapOperation(
      method = "cleanup",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lit/unimi/dsi/fastutil/objects/ReferenceCollection;removeIf(Ljava/util/function/Predicate;)Z"))
  private boolean portalix$expireOldest(
      ReferenceCollection<ClonedChunkSection> entries,
      Predicate<? super ClonedChunkSection> expired,
      Operation<Boolean> original) {
    // Access order is also timestamp order, so only the expired prefix needs visiting.
    boolean removed = false;
    while (!positionToEntry.isEmpty()) {
      var oldest = positionToEntry.get(positionToEntry.firstLongKey());
      if (!expired.test(oldest)) break;
      positionToEntry.removeFirst();
      removed = true;
    }
    return removed;
  }
}

package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.ColumnSectionMap;
import dev.portalix.client.compat.sodium.SodiumColumnRetirement;
import dev.portalix.client.portal.scene.PortalSceneManager;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "net.caffeinemc.mods.sodium.client.render.chunk.storage.QueuedSectionStorage",
    remap = false)
abstract class SodiumSectionStorageMixin implements SodiumColumnRetirement {
  @Shadow @Final @Mutable private Long2ReferenceMap<RenderSection> sections;
  @Shadow @Final @Mutable private Long2ReferenceMap<RenderSection> queuedPuts;
  @Shadow @Final private LongSet queuedRemovals;
  @Shadow private boolean isQueueing;
  @Shadow private int lastPutQueueSize;
  @Shadow private int lastRemoveQueueSize;
  @Unique private boolean portalix$removingColumns;
  @Unique private final LongSet portalix$removedColumns = new LongOpenHashSet();

  @Override
  public void portalix$beginColumns() {
    portalix$removingColumns = true;
  }

  @Override
  public void portalix$endColumns() {
    portalix$removingColumns = false;
  }

  @WrapOperation(
      method = "queueRemove",
      at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;add(J)Z"))
  private boolean portalix$columnRemoval(LongSet removals, long key, Operation<Boolean> original) {
    return portalix$removingColumns
        ? portalix$removedColumns.add(key & -ColumnSectionMap.HEIGHT)
        : original.call(removals, key);
  }

  @WrapOperation(
      method = "getConsistent",
      at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;contains(J)Z"))
  private boolean portalix$columnAbsent(LongSet removals, long key, Operation<Boolean> original) {
    return portalix$removedColumns.contains(key & -ColumnSectionMap.HEIGHT)
        || original.call(removals, key);
  }

  @Inject(method = "<init>", at = @At("TAIL"))
  private void portalix$initialCapacity(CallbackInfo ci) {
    sections = new ColumnSectionMap<>();
    queuedPuts = new ColumnSectionMap<>();
    if (!PortalSceneManager.isPortalView()) return;
    var level = Minecraft.getInstance().level;
    int height = level.getMaxSectionY() - level.getMinSectionY() + 1;
    int diameter = 2 * Minecraft.getInstance().options.getEffectiveRenderDistance() + 3;
    int count =
        Math.min(ChunkTrackerHolder.get(level).getReadyChunks().size(), diameter * diameter)
            * height;
    ((ColumnSectionMap<RenderSection>) sections).ensureCapacity(count);
    ((ColumnSectionMap<RenderSection>) queuedPuts).ensureCapacity(count);
  }

  @WrapMethod(method = "endSafeReadPhase")
  private void portalix$publishColumns(Operation<Void> original) {
    int removed = 0;
    if (isQueueing) {
      var current = (ColumnSectionMap<RenderSection>) sections;
      if (!portalix$removedColumns.isEmpty()) {
        for (long column : portalix$removedColumns) removed += current.removeColumn(column);
        lastPutQueueSize = 0;
        lastRemoveQueueSize = 0;
      }
      current.ensureCapacity(sections.size() + queuedPuts.size());
    }
    original.call();
    lastRemoveQueueSize += removed;
    portalix$removedColumns.clear();
  }

  @WrapOperation(
      method = "endSafeReadPhase",
      at = @At(value = "INVOKE", target =
          "Lit/unimi/dsi/fastutil/objects/ObjectSet;iterator()Lit/unimi/dsi/fastutil/objects/ObjectIterator;"))
  private ObjectIterator<Long2ReferenceMap.Entry<RenderSection>> portalix$queuedEntries(
      ObjectSet<Long2ReferenceMap.Entry<RenderSection>> entries,
      Operation<ObjectIterator<Long2ReferenceMap.Entry<RenderSection>>> original) {
    return ((Long2ReferenceMap.FastEntrySet<RenderSection>) entries).fastIterator();
  }

  @Inject(method = "deleteAll", at = @At("RETURN"))
  private void portalix$clearColumns(CallbackInfo ci) {
    portalix$removedColumns.clear();
  }
}

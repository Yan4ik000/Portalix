package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumTaskListAccess;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.DeferredTaskList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.TaskCollectingTree;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(value = DeferredTaskList.class, remap = false)
abstract class SodiumTaskListMixin extends LongHeapPriorityQueue implements SodiumTaskListAccess {
  @Shadow @Final private int baseOffsetX;
  @Shadow @Final private int baseOffsetZ;

  @Override
  public long[] portalix$sectionSnapshot() {
    long[] sections = new long[size];
    for (int i = 0; i < size; i++) {
      long encoded = heap[i];
      int x = (int) (encoded >>> 20) & 1023;
      int y = (int) (encoded >>> 10) & 1023;
      int z = (int) encoded & 1023;
      sections[i] = SectionPos.asLong(
          x + baseOffsetX, y + TaskCollectingTree.SECTION_Y_MIN, z + baseOffsetZ);
    }
    return sections;
  }
}

package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.InnerPartitionBSPNode",
    remap = false)
abstract class SodiumBspReuseMixin implements Cloneable {
  @WrapMethod(method = "attemptNodeReuse")
  private static @Coerce Object portalix$immutablePublishedTree(
      @Coerce Object workspace,
      IntArrayList indices,
      @Coerce Object oldNode,
      Operation<Object> original) {
    // Portal sorts may still read the previous mesh while the next mesh remaps this node.
    return original.call(
        workspace,
        indices,
        oldNode == null ? null : ((SodiumBspReuseMixin) oldNode).portalix$copy());
  }

  @Unique
  private Object portalix$copy() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException exception) {
      throw new AssertionError(exception);
    }
  }
}

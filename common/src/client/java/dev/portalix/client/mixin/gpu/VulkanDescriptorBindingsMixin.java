package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanRenderPass.class)
abstract class VulkanDescriptorBindingsMixin {
  @Shadow protected VulkanRenderPipeline pipeline;
  @Shadow private boolean anyDescriptorDirty;
  @Unique private boolean portalix$fullPush = true;
  @Unique private boolean portalix$updating;
  @Unique private boolean portalix$publishingAll;
  @Unique private VulkanRenderPipeline portalix$boundPipeline;
  @Unique private BitSet portalix$dirty;
  @Unique private List<VulkanBindGroupLayout.Entry> portalix$entries;
  @Unique private ArrayList<VulkanBindGroupLayout.Entry> portalix$partialEntries;
  @Unique private IntArrayList portalix$bindings;

  @Inject(method = "setPipeline", at = @At("HEAD"))
  private void portalix$pipelineChanged(CallbackInfo ci) {
    portalix$fullPush = true;
  }

  // Track storage mutations so every setter uses the same descriptor invalidation path.
  @WrapOperation(
      method = "*",
      at = @At(value = "INVOKE", target =
          "Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
      require = 3)
  private Object portalix$bindingChanged(
      HashMap<Object, Object> bindings, Object name, Object value, Operation<Object> original) {
    Object previous = original.call(bindings, name, value);
    portalix$markBinding(name);
    return previous;
  }

  @WrapOperation(
      method = "*",
      at = @At(value = "INVOKE", target =
          "Ljava/util/HashMap;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
  private Object portalix$bindingRemoved(
      HashMap<Object, Object> bindings, Object name, Operation<Object> original) {
    Object previous = original.call(bindings, name);
    portalix$markBinding(name);
    return previous;
  }

  @Unique
  private void portalix$markBinding(Object name) {
    anyDescriptorDirty = true;
    if (portalix$fullPush || pipeline != portalix$boundPipeline) return;
    var entries = pipeline.layout().entries();
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).name().equals(name)) {
        if (portalix$dirty == null) portalix$dirty = new BitSet(entries.size());
        portalix$dirty.set(i);
      }
    }
  }

  @Inject(method = "pushDescriptors", at = @At("HEAD"))
  private void portalix$selectBindings(CallbackInfo ci) {
    portalix$updating = anyDescriptorDirty;
    if (!portalix$updating) return;
    var entries = pipeline.layout().entries();
    portalix$publishingAll = portalix$fullPush || pipeline != portalix$boundPipeline;
    if (portalix$publishingAll) {
      portalix$entries = entries;
    } else if (portalix$dirty == null || portalix$dirty.isEmpty()) {
      portalix$entries = List.of();
    } else {
      if (portalix$partialEntries == null) {
        portalix$partialEntries = new ArrayList<>();
        portalix$bindings = new IntArrayList();
      }
      portalix$partialEntries.clear();
      portalix$bindings.clear();
      for (int i = portalix$dirty.nextSetBit(0); i >= 0; i = portalix$dirty.nextSetBit(i + 1)) {
        portalix$partialEntries.add(entries.get(i));
        portalix$bindings.add(i);
      }
      portalix$entries = portalix$partialEntries;
    }
  }

  @WrapOperation(
      method = "pushDescriptors",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/vulkan/VulkanBindGroupLayout;entries()Ljava/util/List;"))
  private List<VulkanBindGroupLayout.Entry> portalix$changedEntries(
      VulkanBindGroupLayout layout, Operation<List<VulkanBindGroupLayout.Entry>> original) {
    return portalix$entries;
  }

  @WrapOperation(
      method = "pushDescriptors",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/lwjgl/vulkan/VkWriteDescriptorSet;dstBinding(I)Lorg/lwjgl/vulkan/VkWriteDescriptorSet;"))
  private VkWriteDescriptorSet portalix$bindingNumber(
      VkWriteDescriptorSet write, int index, Operation<VkWriteDescriptorSet> original) {
    return original.call(write, portalix$publishingAll ? index : portalix$bindings.getInt(index));
  }

  @WrapOperation(
      method = "pushDescriptors",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/lwjgl/vulkan/KHRPushDescriptor;vkCmdPushDescriptorSetKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;IJILorg/lwjgl/vulkan/VkWriteDescriptorSet$Buffer;)V"))
  private void portalix$pushChanges(
      VkCommandBuffer commands,
      int bindPoint,
      long layout,
      int set,
      VkWriteDescriptorSet.Buffer writes,
      Operation<Void> original) {
    if (writes.hasRemaining()) original.call(commands, bindPoint, layout, set, writes);
  }

  @Inject(method = "pushDescriptors", at = @At("RETURN"))
  private void portalix$publishedBindings(CallbackInfo ci) {
    if (!portalix$updating) return;
    if (portalix$dirty != null) portalix$dirty.clear();
    portalix$boundPipeline = pipeline;
    portalix$fullPush = false;
  }
}

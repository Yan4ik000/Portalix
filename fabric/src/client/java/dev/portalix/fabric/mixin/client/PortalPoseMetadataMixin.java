package dev.portalix.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.fabric.PlayerPoseData;
import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
abstract class PortalPoseMetadataMixin {
  @WrapOperation(method = "handleSetEntityData", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V"),
      require = 1, allow = 1)
  private void portalix$acceptPose(
      SynchedEntityData data, List<SynchedEntityData.DataValue<?>> values,
      Operation<Void> original, @Local Entity entity) {
    List<SynchedEntityData.DataValue<?>> immediate = values;
    if (entity instanceof Player player && player instanceof PlayerPoseData pose) {
      for (var value : values) {
        if (pose.portalix$poseField(value.id())
            && !PortalPassagePose.acceptNetworkScale(player, (Float) value.value())) {
          if (immediate == values) immediate = new ArrayList<>(values);
          immediate.remove(value);
        }
      }
    }
    original.call(data, immediate);
  }
}

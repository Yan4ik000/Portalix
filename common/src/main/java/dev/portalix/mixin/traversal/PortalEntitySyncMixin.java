package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.platform.PortalPlatform;
import dev.portalix.portal.traversal.PortalEntityInitialization;
import dev.portalix.portal.traversal.PortalTrackingSender;
import dev.portalix.portal.traversal.ProjectileStateSync;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public abstract class PortalEntitySyncMixin {
  @Shadow @Final private Entity entity;
  @Shadow @Final private ServerEntity.Synchronizer synchronizer;

  @Inject(method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
      at = @At("TAIL"), require = 1, allow = 1)
  private void portalix$initialState(ServerPlayer observer,
      Consumer<Packet<? super ClientGamePacketListener>> packets, CallbackInfo callback) {
    PortalEntityInitialization.send(entity, observer, packets);
  }

  @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, require = 1, allow = 1)
  private static ServerEntity.Synchronizer portalix$sender(
      ServerEntity.Synchronizer original, @Local(argsOnly = true) Entity entity) {
    return new PortalTrackingSender(entity, original);
  }

  @WrapMethod(method = "sendChanges")
  private void portalix$tracking(Operation<Void> original) {
    var sender = (PortalTrackingSender) synchronizer;
    sender.begin();
    try {
      original.call();
      sender.finish();
      ProjectileStateSync.afterTracking(entity);
      // Remote interpolation must receive the crossing before its attachment pose update.
      PortalPlatform.INSTANCE.syncPortalData(entity);
    } finally {
      sender.clear();
    }
  }
}

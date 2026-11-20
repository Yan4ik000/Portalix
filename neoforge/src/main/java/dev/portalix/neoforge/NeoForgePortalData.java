package dev.portalix.neoforge;

import dev.portalix.PortalixMod;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.portal.traversal.PortalCrossingSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

public final class NeoForgePortalData {
  private static final Quaternionfc IDENTITY = new Quaternionf();
  private static final DeferredRegister<AttachmentType<?>> TYPES =
      DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PortalixMod.MOD_ID);
  private static final DeferredHolder<AttachmentType<?>, AttachmentType<PoseScale>> POSE =
      TYPES.register("passage_pose", () -> AttachmentType.builder(PoseScale::new)
          .sync(new AttachmentSyncHandler<PoseScale>() {
            @Override
            public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer player) {
              var state = holder.getExistingDataOrNull(POSE);
              return state == null || state.syncState != SyncState.DEFAULT;
            }

            @Override
            public void write(RegistryFriendlyByteBuf buffer, PoseScale attachment, boolean initial) {
              buffer.writeFloat(attachment.value);
            }

            @Override
            public PoseScale read(IAttachmentHolder holder, RegistryFriendlyByteBuf buffer, @Nullable PoseScale previous) {
              float scale = buffer.readFloat();
              Player player = (Player) holder;
              var state = previous == null ? player.getData(POSE) : previous;
              if (PortalPassagePose.acceptNetworkScale(player, scale)) state.set(scale);
              return state;
            }
          }).build());
  private static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemRotation>> ROTATION =
      TYPES.register("item_rotation", () -> AttachmentType.builder(ItemRotation::new)
          .sync(new AttachmentSyncHandler<ItemRotation>() {
            @Override
            public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer player) {
              var state = holder.getExistingDataOrNull(ROTATION);
              return state == null || state.syncState != SyncState.DEFAULT;
            }

            @Override
            public void write(RegistryFriendlyByteBuf buffer, ItemRotation attachment, boolean initial) {
              FriendlyByteBuf.writeQuaternion(buffer, attachment.value);
            }

            @Override
            public ItemRotation read(IAttachmentHolder holder, RegistryFriendlyByteBuf buffer, @Nullable ItemRotation previous) {
              var state = previous == null ? holder.getData(ROTATION) : previous;
              state.value = buffer.readQuaternion();
              return state;
            }
          }).build());

  private NeoForgePortalData() {}

  static void register(IEventBus bus) {
    TYPES.register(bus);
  }

  public static float poseScale(Player player) {
    var state = player.getExistingDataOrNull(POSE);
    return state == null ? 1 : state.value;
  }

  public static void poseScale(Player player, float scale) {
    if (poseScale(player) == scale) return;
    player.getData(POSE).set(scale);
  }

  public static Quaternionfc itemRotation(ItemEntity item) {
    var state = item.getExistingDataOrNull(ROTATION);
    return state == null ? IDENTITY : state.value;
  }

  public static void itemRotation(ItemEntity item, Quaternionfc rotation) {
    var state = item.getData(ROTATION);
    if (state.value.equals(rotation)) return;
    state.value = new Quaternionf(rotation);
    state.syncState = item.level().isClientSide() ? SyncState.SYNCED : SyncState.DIRTY;
  }

  static void sync(Entity entity) {
    var pose = entity.getExistingDataOrNull(POSE);
    var rotation = entity.getExistingDataOrNull(ROTATION);
    if ((pose == null || pose.syncState != SyncState.DIRTY)
        && (rotation == null || rotation.syncState != SyncState.DIRTY)) return;
    if (entity.isPassenger() || !entity.getPassengers().isEmpty())
      PortalCrossingSync.afterPassengerUpdates(entity, () -> flush(entity));
    else flush(entity);
  }

  private static void flush(Entity entity) {
    var pose = entity.getExistingDataOrNull(POSE);
    if (pose != null && pose.syncState == SyncState.DIRTY) {
      pose.syncState = SyncState.SYNCED;
      entity.syncData(POSE);
    }
    var rotation = entity.getExistingDataOrNull(ROTATION);
    if (rotation != null && rotation.syncState == SyncState.DIRTY) {
      rotation.syncState = SyncState.SYNCED;
      entity.syncData(ROTATION);
    }
  }

  // Allocation triggers a NeoForge sync before the first change can enter the tracking flush.
  private enum SyncState { DEFAULT, DIRTY, SYNCED }

  private static final class PoseScale {
    private final Player player;
    private float value = 1;
    private SyncState syncState = SyncState.DEFAULT;

    private PoseScale(IAttachmentHolder holder) {
      player = (Player) holder;
    }

    private void set(float scale) {
      if (value == scale) return;
      value = scale;
      syncState = player.level().isClientSide() ? SyncState.SYNCED : SyncState.DIRTY;
      PortalPassagePose.changed(player);
    }
  }

  private static final class ItemRotation {
    private Quaternionf value = new Quaternionf();
    private SyncState syncState = SyncState.DEFAULT;
  }
}

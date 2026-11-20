package dev.portalix.test.mixin;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerImplAccessor {
  @Accessor("keepAlivePending") boolean portalix$keepAlivePending();
  @Accessor("keepAlivePending") void portalix$keepAlivePending(boolean pending);
  @Accessor("keepAliveChallenge") long portalix$keepAliveChallenge();
  @Accessor("keepAliveChallenge") void portalix$keepAliveChallenge(long challenge);
  @Accessor("keepAliveTime") long portalix$keepAliveTime();
  @Accessor("keepAliveTime") void portalix$keepAliveTime(long time);
  @Accessor("latency") int portalix$latency();
  @Accessor("latency") void portalix$latency(int latency);
}

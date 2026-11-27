package dev.portalix.client.portal.terrain;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;

public interface PortalGraphColumns {
  Long2ObjectMap<LongList> portalix$sectionsWaitingForChunkLoads();
}

package dev.portalix.client.compat.sodium;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.function.Consumer;

public interface SodiumTerrainPipelines {
  void portalix$visitTerrainPipelines(Consumer<RenderPipeline> visitor);
}

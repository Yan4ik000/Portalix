package dev.portalix.client.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector3fc;

public interface ClientPlatform {
  ClientPlatform INSTANCE =
      ServiceLoader.load(ClientPlatform.class, ClientPlatform.class.getClassLoader())
          .findFirst()
          .orElseThrow();

  enum Phase {
    SOLID,
    AFTER_TERRAIN,
    TRANSLUCENT_CUSTOM_GEOMETRY
  }

  void registerFeatures();

  void registerReloadListener(Identifier id, PreparableReloadListener listener);

  KeyMapping.Category registerKeyCategory(Identifier id);

  void registerKeyMapping(KeyMapping mapping);

  boolean canServerReceive(CustomPacketPayload.Type<?> type);

  void submit(OrderedSubmitNodeCollector collector, Phase phase, SubmitNode node);

  void registerRenderEvents();

  RenderPipeline pipeline(PipelineVariant variant);

  void chunkLoaded(ClientLevel level, LevelChunk chunk);

  void chunkUnloaded(ClientLevel level, LevelChunk chunk);

  Supplier<Vector3fc[]> itemExtents(QuadCollection quads, Supplier<Vector3fc[]> original);
}

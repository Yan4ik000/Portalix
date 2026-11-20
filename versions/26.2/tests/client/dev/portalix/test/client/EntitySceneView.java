package dev.portalix.test.client;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class EntitySceneView {
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final long firstFrame;

  EntitySceneView(Minecraft client, ScenarioFrames frames) {
    this.client = client;
    this.frames = frames;
    firstFrame = FrameCapture.presentation().frameId();
  }

  boolean ready() {
    var window = client.getWindow();
    var presented = FrameCapture.presentation();
    if (client.gui.screen() != null || client.gui.overlay() != null || window.isMinimized()
        || !client.player.onGround() || !client.levelRenderer.hasRenderedAllSections()
        || presented.frameId() <= firstFrame || presented.camera() == null
        || presented.camera().position().distanceTo(client.player.getEyePosition()) > .1) return false;
    int cx = client.player.blockPosition().getX() >> 4, cz = client.player.blockPosition().getZ() >> 4;
    for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++)
      if (client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) == null) return false;
    return true;
  }

  Map<String, Object> diagnostics() {
    var data = new LinkedHashMap<String, Object>();
    data.put("screen", client.gui.screen() == null ? null : client.gui.screen().getClass().getSimpleName());
    data.put("minimized", client.getWindow().isMinimized());
    var presentation = FrameCapture.presentation();
    data.put("presentedFrameId", presentation.frameId());
    data.put("presentedNanos", presentation.nanos());
    data.put("observedNanos", FrameCapture.monotonicNanos());
    data.put("presentedWorldCamera", presentation.camera() == null ? null : presentation.camera().position());
    data.put("currentWorldCamera", FrameCapture.worldCamera() == null ? null : FrameCapture.worldCamera().position());
    data.put("auditedFrames", frames.presentedFrames());
    data.put("submittedFrames", frames.submittedFrames());
    data.put("sectionsComplete", client.levelRenderer.hasRenderedAllSections());
    data.put("position", client.player.position());
    data.put("onGround", client.player.onGround());
    data.put("flying", client.player.getAbilities().flying);
    return data;
  }

  void record(String name) {
    try {
      Files.createDirectories(frames.directory());
      Files.writeString(frames.directory().resolve(name + ".json"),
          new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(diagnostics()));
    } catch (IOException failure) { throw new UncheckedIOException(failure); }
  }
}

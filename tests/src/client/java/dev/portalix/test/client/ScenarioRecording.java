package dev.portalix.test.client;

import com.google.gson.GsonBuilder;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.util.Util;

/** Small review images from actual presented frames; assertions still use full resolution captures. */
final class ScenarioRecording {
  private static final int WIDTH = 696;
  private static final int HEIGHT = 392;
  private final Path directory;
  private final List<Map<String, Object>> entries = new ArrayList<>();
  private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
  private CompletableFuture<Path> completion;
  private int accepted;
  private long firstPresentationNanos = -1;
  private long previousFrame = -1;
  private long previousPresentationNanos = -1;
  private int previousPlayerTick = -1;
  private int world;
  private int previousWorld = -1;

  ScenarioRecording(Path evidenceDirectory) {
    directory = Objects.requireNonNull(evidenceDirectory);
  }

  synchronized void worldBoundary() { world++; }

  synchronized CompletableFuture<Void> accept(FrameCapture.Frame frame, String stage, boolean gameplay) {
    Objects.requireNonNull(frame);
    Objects.requireNonNull(stage);
    if (completion != null) throw new IllegalStateException("The recording is already finishing");
    int index = accepted++;
    int frameWorld = world;
    tail = tail.thenRunAsync(() -> writeFrame(frame, stage, index, frameWorld, gameplay), Util.ioPool());
    return tail;
  }

  synchronized CompletableFuture<Path> finish() {
    if (completion == null) completion = tail.thenApplyAsync(ignored -> writeTimeline(), Util.ioPool());
    return completion;
  }

  private void writeFrame(FrameCapture.Frame frame, String stage, int index, int world, boolean gameplay) {
    if (frame.id() <= previousFrame) throw new IllegalArgumentException("Recording frames must be in presentation order");
    if (frame.presentationNanos() <= previousPresentationNanos)
      throw new IllegalArgumentException("Recording presentation clocks must be strictly increasing");
    if (world == previousWorld && frame.playerTick() < previousPlayerTick)
      throw new IllegalArgumentException("Recording player ticks must not move backwards within the gameplay world");
    previousFrame = frame.id();
    previousPresentationNanos = frame.presentationNanos();
    previousPlayerTick = frame.playerTick();
    previousWorld = world;
    if (index == 0) firstPresentationNanos = frame.presentationNanos();
    double time = (frame.presentationNanos() - firstPresentationNanos) / 1_000_000_000.0;
    try {
      String fileName = String.format(java.util.Locale.ROOT, "frame-%05d.jpg", index);
      Path path = directory.resolve("timeline").resolve(fileName);
      Files.createDirectories(path.getParent());
      if (Files.exists(path)) throw new IOException("Recording image already exists: " + path);
      BufferedImage source = frame.toBufferedImage();
      BufferedImage thumbnail = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = thumbnail.createGraphics();
      try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
      } finally {
        graphics.dispose();
      }
      if (!ImageIO.write(thumbnail, "jpg", path.toFile())) throw new IOException("No JPEG writer is available");

      Map<String, Object> entry = new LinkedHashMap<>(frame.metadata());
      entry.put("index", index);
      entry.put("timeSeconds", time);
      entry.put("stage", stage);
      entry.put("world", world);
      entry.put("gameplay", gameplay);
      entry.put("image", "timeline/" + fileName);
      entries.add(entry);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private Path writeTimeline() {
    if (entries.isEmpty()) throw new IllegalStateException("Cannot finish an empty recording");
    Map<String, Object> recording = new LinkedHashMap<>();
    recording.put("samplingIntervalPlayerTicks", 4);
    recording.put("imageWidth", WIDTH);
    recording.put("imageHeight", HEIGHT);
    recording.put("timeSource", "monotonic presentation nanoseconds");
    recording.put("frames", entries);
    String json = new GsonBuilder().setPrettyPrinting().create().toJson(recording);
    Path html = directory.resolve("timeline.html");
    try {
      Files.createDirectories(directory);
      Files.writeString(html, HTML.replace("/*RECORDING_DATA*/", json));
      return html;
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static final String HTML = """
      <!doctype html>
      <html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <title>Portalix — запись сценария</title>
      <style>
      :root{color-scheme:dark;font:16px system-ui;background:#11151b;color:#edf3fa}
      body{max-width:1100px;margin:24px auto;padding:0 20px}h1{font-size:20px;font-weight:500}
      #stage{font-size:30px;font-weight:700;min-height:2.5em;margin:14px 0}
      img{display:block;width:100%;height:auto;aspect-ratio:696/392;object-fit:contain;background:#000;border:1px solid #394452}
      nav{display:flex;align-items:center;gap:12px;margin:16px 0}input{flex:1}button,select{font:inherit;padding:8px}
      #detail{font:13px ui-monospace,monospace;color:#b4c6d8;white-space:pre-wrap}small{color:#b4c6d8}
      </style>
      <body><h1>Portalix — фактические кадры окна</h1>
      <small>Воспроизведение по реальному времени представления кадров. Проверки используют исходные кадры полного разрешения.</small>
      <div id="stage"></div><img id="image" alt="Кадр игрового окна">
      <nav><button id="play">Воспроизвести</button><input id="seek" type="range" min="0" step="1" value="0" aria-label="Кадр">
      <select id="speed" aria-label="Скорость"><option value="0.5">0.5×</option><option value="1" selected>1×</option><option value="2">2×</option></select></nav>
      <div id="detail"></div>
      <script id="recording" type="application/json">/*RECORDING_DATA*/</script>
      <script>
      const recording=JSON.parse(document.querySelector('#recording').textContent),frames=recording.frames;
      const image=document.querySelector('#image'),stage=document.querySelector('#stage'),seek=document.querySelector('#seek');
      const detail=document.querySelector('#detail'),play=document.querySelector('#play'),speed=document.querySelector('#speed');
      let index=0,playing=false,anchorWall=0,anchorTime=0;
      seek.max=frames.length-1;
      function show(i){index=i;const f=frames[i];seek.value=i;image.src=f.image;stage.textContent=f.stage;
        detail.textContent=`${f.timeSeconds.toFixed(2)} с · кадр ${i+1}/${frames.length} · presentation ${f.frameId} · tick ${f.gameTick}\\n`
          +`camera ${JSON.stringify(f.cameraPosition||null)} · yaw ${f.yaw??'—'} · pitch ${f.pitch??'—'}`;}
      function anchor(){anchorWall=performance.now();anchorTime=frames[index].timeSeconds;}
      function pause(){playing=false;play.textContent='Воспроизвести';}
      play.onclick=()=>{if(playing){pause();return;}if(index===frames.length-1)show(0);anchor();playing=true;play.textContent='Пауза';};
      seek.oninput=()=>{show(Number(seek.value));anchor();};speed.onchange=anchor;
      function update(now){if(playing){const time=anchorTime+(now-anchorWall)/1000*Number(speed.value);
        let next=index;while(next+1<frames.length&&frames[next+1].timeSeconds<=time)next++;
        if(next!==index)show(next);if(index===frames.length-1&&time>=frames[index].timeSeconds+0.2)pause();}
        requestAnimationFrame(update);}
      show(0);requestAnimationFrame(update);
      </script></body></html>
      """;
}

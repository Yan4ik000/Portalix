package dev.portalix.test.client;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.platform.ModPresence;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;

public final class ClientRegressionRunner {
  public interface ShaderEnvironment {
    ShaderConfiguration verify();
    void reloadShadersAfterResourceReload();
  }

  public record ShaderConfiguration(String pack, String profile, java.util.Map<String, String> settings) {}

  private enum Stage { START, ENVIRONMENT, REPLACE_WORLD, WORLD, SCENE, DRAIN, CLEANUP, RECORDING, DONE }
  private static final String PROFILE = System.getProperty("portalix.test.profile");
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final long START = System.nanoTime();
  private static final List<String> PASSED = new ArrayList<>();
  private static Stage stage = Stage.START;
  private static long stageStarted = START;
  private static String backend;
  private static String shader;
  private static String shaderProfile;
  private static final java.util.Map<String, String> SHADER_SETTINGS = new LinkedHashMap<>();
  private static String options;
  private static ScenarioFrames frames;
  private static final String SELECTION = System.getProperty("portalix.test.clientTest", "all");
  private static final List<ClientSystemScenes> SELECTED = ClientSystemScenes.select(SELECTION);
  private static final List<java.util.Map<String, Object>> SCENES = new ArrayList<>();
  private static int sceneIndex;
  private static ClientSystemScene scene;
  private static long sceneStarted;
  private static long sceneProgress;
  private static int sceneMilestones;
  private static CompletableFuture<Void> cleanup;
  private static CompletableFuture<Path> recording;
  private static Throwable failure;
  private static java.util.Map<String, Object> failureContext = java.util.Map.of();
  private static java.util.Map<String, Object> performanceFixture = java.util.Map.of("state", "generated-on-demand");

  private ClientRegressionRunner() {}

  public static boolean active() { return PROFILE != null; }

  public static void frameRendered(RenderTarget target) {
    if (active()) {
      if (frames != null) frames.onFrame(target);
      FrameCapture.onFrame(target);
      if (stage == Stage.SCENE && scene != null) scene.presentedFrame(FrameCapture.monotonicNanos());
    }
  }

  public static void tick(Minecraft client) {
    if (!active() || stage == Stage.DONE) return;
    try {
      if (stage == Stage.SCENE) {
        int milestones = frames.checkpoints().size() + scene.passed().size();
        if (milestones > sceneMilestones) {
          sceneMilestones = milestones;
          sceneProgress = System.nanoTime();
        }
      }
      long deadline = stage == Stage.START || stage == Stage.WORLD && sceneIndex == 0 ? START
          : stage == Stage.SCENE ? sceneProgress : stageStarted;
      long limit = stage == Stage.DRAIN || stage == Stage.CLEANUP || stage == Stage.RECORDING
          ? Duration.ofSeconds(45).toNanos() : Duration.ofMinutes(3).toNanos();
      ClientAssertions.check(System.nanoTime() - deadline < limit,
          "Regression timed out during " + stage + " / " + SELECTED.get(sceneIndex).id());
      if (frames != null) frames.tick(SELECTED.get(sceneIndex).id() + "/" + stage.name().toLowerCase(java.util.Locale.ROOT));
      switch (stage) {
        case START -> {
          if (!client.isGameLoadFinished() || client.gui.overlay() != null) return;
          configureGraphics(client);
          client.getWindow().setWindowed(1856, 1044);
          transition(Stage.ENVIRONMENT);
        }
        case ENVIRONMENT -> {
          verifyEnvironment(client);
          PASSED.add("environment/actual renderer, dependencies, shader and window settings");
          createWorld(client);
        }
        case REPLACE_WORLD -> {
          if (client.level != null || client.getSingleplayerServer() != null) return;
          createWorld(client);
        }
        case WORLD -> {
          if (client.level == null || client.player == null || client.gui.screen() != null
              || client.gui.overlay() != null) return;
          beginScene(client);
        }
        case SCENE -> {
          client.gui.hud.setOverlayMessage(Component.literal("Portalix: " + SELECTED.get(sceneIndex).id()), false);
          if (scene.tick()) {
            new ScenarioInput(client).stop();
            transition(Stage.DRAIN);
          }
        }
        case DRAIN -> {
          if (!frames.pause()) return;
          startCleanup();
        }
        case CLEANUP -> {
          if (!cleanup.isDone()) return;
          cleanup.join();
          recording = frames.finish();
          transition(Stage.RECORDING);
        }
        case RECORDING -> {
          if (!recording.isDone()) return;
          recording.join();
          recordScene();
          if (failure != null || ++sceneIndex == SELECTED.size()) finish(client, failure);
          else {
            frames = null;
            scene = null;
            if (SELECTED.get(sceneIndex) == ClientSystemScenes.PERFORMANCE) {
              client.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
              transition(Stage.REPLACE_WORLD);
            } else beginScene(client);
          }
        }
        default -> {}
      }
    } catch (Throwable problem) {
      if (failure == null) {
        failure = problem;
        var window = client.getWindow();
        var presented = FrameCapture.presentation();
        failureContext = java.util.Map.of("stage", stage.name(),
            "surfaceAcquired", client.windowSurface().isAcquired(),
            "minimized", window.isMinimized(), "focused", window.isFocused(),
            "width", window.getWidth(), "height", window.getHeight(),
            "lastPresentedFrame", presented.frameId(),
            "lastPresentedAgoMs", (FrameCapture.monotonicNanos() - presented.nanos()) / 1e6);
      }
      else if (failure != problem) failure.addSuppressed(problem);
      new ScenarioInput(client).stop();
      if (frames == null || scene == null) finish(client, failure);
      else if (stage == Stage.DRAIN) {
        frames.abortPending();
        try { startCleanup(); }
        catch (Throwable cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
          recording = frames.finish();
          transition(Stage.RECORDING);
        }
      }
      else if (stage == Stage.RECORDING) {
        recordScene();
        finish(client, failure);
      } else if (stage == Stage.CLEANUP) {
        if (cleanup != null && !cleanup.isDone()
            && System.nanoTime() - stageStarted < Duration.ofSeconds(45).toNanos()) return;
        recording = frames.finish();
        transition(Stage.RECORDING);
      } else {
        frames.abortPending();
        transition(Stage.DRAIN);
      }
    }
  }

  private static void transition(Stage next) {
    stage = next;
    stageStarted = System.nanoTime();
    LOGGER.info("PORTALIX REGRESSION {}: {}", PROFILE, next);
  }

  private static void createWorld(Minecraft client) {
    String world = "portalix-regression-" + UUID.randomUUID();
    var configuration = WorldDataConfiguration.DEFAULT;
    var settings = new LevelSettings(world, GameType.CREATIVE,
        new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, true), true,
        configuration);
    boolean performance = SELECTED.get(sceneIndex) == ClientSystemScenes.PERFORMANCE;
    String regions = System.getProperty("portalix.test.pregeneratedRegions");
    if (regions != null) {
      ClientAssertions.check(performance, "Pregenerated regions are only supported by the performance benchmark");
      preparePerformanceRegions(client, world, Path.of(regions));
    }
    transition(Stage.WORLD);
    client.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(1, performance, false),
        lookup -> lookup.lookupOrThrow(Registries.WORLD_PRESET)
            .getOrThrow(performance ? WorldPresets.NORMAL : WorldPresets.FLAT)
            .value().createWorldDimensions(), new TitleScreen());
  }

  static java.util.Map<String, Object> performanceFixture() { return performanceFixture; }

  private static void preparePerformanceRegions(Minecraft client, String world, Path source) {
    ClientAssertions.check(source.isAbsolute() && Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS),
        "Pregenerated regions must name an existing absolute directory: " + source);
    try (var entries = Files.list(source)) {
      var regions = entries.filter(path -> path.getFileName().toString().endsWith(".mca")
          || path.getFileName().toString().endsWith(".mcc")).sorted().toList();
      long regionFiles = regions.stream().filter(path -> path.getFileName().toString().endsWith(".mca")).count();
      ClientAssertions.check(regionFiles > 0, "Pregenerated fixture contains no region files");
      long bytes = 0;
      for (var region : regions) {
        ClientAssertions.check(region.getFileName().toString().matches("(?:r\\.-?\\d+\\.-?\\d+\\.mca|c\\.-?\\d+\\.-?\\d+\\.mcc)")
            && Files.isRegularFile(region, LinkOption.NOFOLLOW_LINKS), "Invalid pregenerated region: " + region);
        bytes += Files.size(region);
      }
      ClientAssertions.check(bytes > 0, "Pregenerated fixture contains no region data");
      var worldDirectory = client.getLevelSource().getLevelPath(world);
      ClientAssertions.check(!Files.exists(worldDirectory, LinkOption.NOFOLLOW_LINKS),
          "Pregenerated fixture requires a fresh world directory: " + worldDirectory);
      var destination = DimensionType.getStorageFolder(Level.OVERWORLD, worldDirectory).resolve("region");
      Files.createDirectories(destination);
      var digest = MessageDigest.getInstance("SHA-256");
      for (var region : regions) {
        digest.update(region.getFileName().toString().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        try (var input = new DigestInputStream(Files.newInputStream(region), digest)) {
          Files.copy(input, destination.resolve(region.getFileName()));
        }
      }
      performanceFixture = java.util.Map.of("state", "disk-snapshot/client-cold",
          "generationCoverage", "unverified",
          "manifestVersion", 1, "source", source.toString(), "regionFiles", regionFiles,
          "externalChunkFiles", regions.size() - regionFiles, "bytes", bytes,
          "sha256", HexFormat.of().formatHex(digest.digest()));
    } catch (IOException failure) {
      throw new UncheckedIOException("Cannot prepare pregenerated performance regions", failure);
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void startCleanup() {
    transition(Stage.CLEANUP);
    cleanup = java.util.Objects.requireNonNull(scene.cleanup(), "Scene cleanup returned no completion");
  }

  private static void beginScene(Minecraft client) {
    verifyEnvironment(client);
    var selected = SELECTED.get(sceneIndex);
    frames = new ScenarioFrames(client, reportDirectory().resolve("evidence").resolve(selected.id()));
    sceneStarted = System.nanoTime();
    sceneProgress = sceneStarted;
    sceneMilestones = 0;
    scene = null;
    scene = selected.create(client, frames);
    cleanup = null;
    recording = null;
    transition(Stage.SCENE);
  }

  private static void recordScene() {
    var entry = new LinkedHashMap<String, Object>();
    entry.put("id", SELECTED.get(sceneIndex).id());
    entry.put("status", failure == null ? "passed" : "failed");
    entry.put("durationSeconds", (System.nanoTime() - sceneStarted) / 1e9);
    var names = scene.passed();
    entry.put("passed", names);
    entry.put("cases", names.stream().map(name -> java.util.Map.of("name", name, "status", "passed")).toList());
    entry.put("checkpoints", frames.checkpoints());
    entry.put("checkedFrames", frames.checkedFrames());
    entry.put("auditedPresentedFrames", frames.presentedFrames());
    entry.put("presentedGameplayFrames", frames.submittedFrames());
    entry.put("diagnostics", System.getProperty("portalix.test.diagnostics", "compact"));
    if (recording.isCompletedExceptionally()) {
      entry.put("recordingStatus", "failed");
    } else {
      var artifact = recording.join();
      if (artifact != null)
        entry.put("recording", "evidence/" + SELECTED.get(sceneIndex).id() + "/" + artifact.getFileName());
    }
    SCENES.add(entry);
    PASSED.addAll(names);
  }

  private static void verifyEnvironment(Minecraft client) {
    String loader = PROFILE.startsWith("fabric-") ? "fabricloader" : "neoforge";
    ClientAssertions.check(ModPresence.INSTANCE.isLoaded(loader), "Requested loader is not active: " + loader);
    backend = RenderSystem.getDevice().getDeviceInfo().backendName();
    boolean vulkan = PROFILE.endsWith("-vulkan");
    boolean sodium = PROFILE.contains("-sodium-");
    boolean iris = PROFILE.contains("-iris-");
    ClientAssertions.check(backend.toLowerCase(java.util.Locale.ROOT).contains(vulkan ? "vulkan" : "opengl"),
        "Requested backend differs from actual " + backend);
    ClientAssertions.check(ModPresence.INSTANCE.isLoaded("sodium") == sodium, "Sodium profile mismatch");
    ClientAssertions.check(ModPresence.INSTANCE.isLoaded("iris") == iris, "Iris profile mismatch");
    if (client.level != null)
      ClientAssertions.check(IrisShaders.active() == iris, "Requested shaders are not active in the test world");
    if (iris) {
      var environment = java.util.ServiceLoader.load(ShaderEnvironment.class).findFirst()
          .orElseThrow(() -> new AssertionError("Iris environment verification is not packaged")).verify();
      shader = environment.pack();
      shaderProfile = environment.profile();
      SHADER_SETTINGS.putAll(environment.settings());
    }
    ClientAssertions.check(!client.getWindow().isFullscreen(), "Tests require windowed mode");
    ClientAssertions.check(client.getWindow().getWidth() == 1856 && client.getWindow().getHeight() == 1044,
        "Expected actual framebuffer 1856x1044, got " + client.getWindow().getWidth() + "x" + client.getWindow().getHeight());
    ClientAssertions.check(!client.options.enableVsync().get(), "VSync must be disabled");
    ClientAssertions.check(client.options.framerateLimit().get() >= 260, "Frame cap must be unlimited");
    ClientAssertions.check(client.options.renderDistance().get() == renderDistance()
        && client.options.simulationDistance().get() == 16, "Requested render/simulation distance differs from the actual settings");
    ClientAssertions.check(Math.abs(client.options.getSoundSourceVolume(SoundSource.MASTER) - .2F) < 1e-6F
        && client.options.getSoundSourceVolume(SoundSource.MUSIC) == 0,
        "Tests require 20% master volume and disabled music");
    client.options.pauseOnLostFocus = false;
    options = client.options.dumpOptionsForReport();
  }

  private static void configureGraphics(Minecraft client) {
    var settings = client.options;
    settings.applyGraphicsPreset(GraphicsPreset.FABULOUS);
    settings.renderDistance().set(renderDistance());
    settings.simulationDistance().set(16);
    maximize(settings.entityDistanceScaling());
    maximize(settings.biomeBlendRadius());
    maximize(settings.mipmapLevels());
    maximize(settings.cloudRange());
    maximize(settings.weatherRadius());
    maximize(settings.maxAnisotropyBit());
    settings.enableVsync().set(false);
    settings.framerateLimit().set(260);
    settings.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
    settings.pauseOnLostFocus = false;
    settings.save();
    options = settings.dumpOptionsForReport();
    LOGGER.info("PORTALIX REGRESSION graphics settings: {}", options);
  }

  private static int renderDistance() {
    if (SELECTED.get(sceneIndex) != ClientSystemScenes.PERFORMANCE) return 16;
    int distance = Integer.parseInt(System.getProperty("portalix.test.benchmarkRenderDistance", "16"));
    ClientAssertions.check(distance == 16 || distance == 32, "Benchmark render distance must be 16 or 32 chunks");
    return distance;
  }

  @SuppressWarnings("unchecked")
  private static <T> void maximize(OptionInstance<T> setting) {
    if (!(setting.values() instanceof OptionInstance.SliderableValueSet<?> slider))
      throw new AssertionError("Expected a bounded graphics slider: " + setting);
    setting.set(((OptionInstance.SliderableValueSet<T>) slider).fromSliderValue(1));
  }

  private static Path reportDirectory() {
    return Path.of(System.getProperty("portalix.test.reportDir", "build/reports/regression/" + PROFILE));
  }

  private static void finish(Minecraft client, Throwable failure) {
    stage = Stage.DONE;
    try {
      Files.createDirectories(reportDirectory());
      var report = new LinkedHashMap<String, Object>();
      report.put("profile", PROFILE);
      report.put("clientTest", SELECTION);
      report.put("scenes", SCENES);
      report.put("status", failure == null ? "passed" : "failed");
      report.put("backend", backend);
      report.put("shader", shader);
      report.put("shaderProfile", shaderProfile);
      report.put("shaderSettings", SHADER_SETTINGS);
      report.put("settings", options);
      report.put("durationSeconds", (System.nanoTime() - START) / 1e9);
      report.put("passed", PASSED);
      var cases = new ArrayList<java.util.Map<String, String>>();
      for (String name : PASSED) cases.add(java.util.Map.of("name", name, "status", "passed"));
      if (failure != null) cases.add(java.util.Map.of("name", "regression", "status", "failed", "message", failure.toString()));
      report.put("cases", cases);
      String trace = "";
      if (failure != null) {
        var buffer = new StringWriter();
        failure.printStackTrace(new PrintWriter(buffer));
        trace = buffer.toString();
        report.put("failure", trace);
        report.put("failureContext", failureContext);
        LOGGER.error("Regression profile failed: {}", PROFILE, failure);
      }
      var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"")
          .append(escape(PROFILE)).append("\" tests=\"").append(PASSED.size() + (failure == null ? 0 : 1))
          .append("\" failures=\"").append(failure == null ? 0 : 1).append("\">");
      for (String name : PASSED) xml.append("<testcase name=\"").append(escape(name)).append("\"/>");
      if (failure != null) xml.append("<testcase name=\"regression\"><failure>").append(escape(trace)).append("</failure></testcase>");
      Files.writeString(reportDirectory().resolve("TEST-regression.xml"), xml.append("</testsuite>").toString());
      Files.writeString(reportDirectory().resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
      LOGGER.info("PORTALIX REGRESSION {}: {} {} groups", PROFILE, report.get("status"), PASSED.size());
    } catch (Exception reportFailure) {
      LOGGER.error("Cannot write regression report: {}", PROFILE, reportFailure);
    } finally {
      try {
        // Finish server ticks and their packet flushes before closing the local connection.
        var server = client.getSingleplayerServer();
        if (server != null) server.halt(true);
        client.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
      } finally {
        client.stop();
      }
    }
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}

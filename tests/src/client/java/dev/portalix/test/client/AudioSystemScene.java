package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import com.google.gson.GsonBuilder;
import dev.portalix.client.gun.sound.SourcePositionalSound;
import dev.portalix.client.portal.audio.PortalAudioChannelAccess;
import dev.portalix.portal.PortalTopology;
import dev.portalix.sound.GunSounds;
import dev.portalix.test.client.mixin.SoundEngineAccessor;
import dev.portalix.test.client.mixin.SoundManagerAccessor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;

final class AudioSystemScene extends PortalPreparedScene {
  private static final int SOURCE_COUNT = 24;
  private enum Step {
    PREPARE, BEFORE, PLAY, ROUTED, OBSTRUCT, OBSCURED, CLEAR, RECOVERED,
    PAUSE, RESUME, STOP_ONE, CLOSE, DIRECT, AFTER, STOP, DONE
  }
  private record Loop(SourcePositionalSound sound, Vec3 emitter, Vec3 audible, boolean routed) {}
  private record Source(int id, int state, float gain, Vec3 position) {}
  private final List<String> passed = new ArrayList<>();
  private final List<Loop> loops = new ArrayList<>();
  private Step step = Step.PREPARE;
  private int age;
  private List<BlockPos> obstacle;
  private List<ChannelAccess.ChannelHandle> handles;
  private CompletableFuture<List<Source>> observation;
  private CompletableFuture<Void> mutation;
  private CompletableFuture<FrameCapture.Frame> screenshot;
  private FrameCapture.Frame before;
  private List<Source> routed;
  private List<Source> obscured;
  private List<Source> direct;

  AudioSystemScene(Minecraft client, ScenarioFrames frames) { super(client, frames); }

  public boolean tick() {
    check(++age < 600, "Real audio routing timed out during " + step);
    switch (step) {
      case PREPARE -> { if (prepare(768, 512)) next(Step.BEFORE); }
      case BEFORE -> {
        if (screenshot == null) { screenshot = frames.checkpoint("audio-live-route"); return false; }
        if (!screenshot.isDone()) return false;
        before = screenshot.join();
        var marker = arrangement.markers().getFirst();
        before.assertMaterial(before.project(arrangement.imageBounds(marker.bounds())).orElseThrow(),
            FrameCapture.Material.RED, .05, 1);
        next(Step.PLAY);
      }
      case PLAY -> {
        Vec3 remote = arrangement.exit().center().add(arrangement.exit().normal().scale(3))
            .add(0, client.player.getEyeHeight() - 1, 0);
        Vec3 listener = client.gameRenderer.mainCamera().position();
        for (int i = 0; i < SOURCE_COUNT; i++) {
          boolean throughPortal = i % 2 == 0;
          Vec3 emitter = throughPortal ? remote.add(arrangement.exit().right().scale(i * .01))
              : listener.add(arrangement.entrance().right().scale(2 + i * .02));
          var sound = new SourcePositionalSound(client, GunSounds.HOLD, null, emitter, true);
          loops.add(new Loop(sound, emitter, throughPortal ? arrangement.imagePoint(emitter) : emitter, throughPortal));
          check(client.getSoundManager().play(sound) != SoundEngine.PlayResult.NOT_STARTED,
              "The native sound engine rejected positional loop " + i);
        }
        var center = BlockPos.containing(arrangement.exit().center().add(arrangement.exit().normal().scale(1.5)));
        obstacle = BlockPos.betweenClosedStream(center.offset(-3, -2, 0), center.offset(3, 4, 0))
            .map(BlockPos::immutable).toList();
        next(Step.ROUTED);
      }
      case ROUTED -> {
        if (!observe()) return false;
        var voices = observation.join(); observation = null;
        for (int i = 0; i < voices.size(); i++) {
          var voice = voices.get(i);
          if (voice.state != AL10.AL_PLAYING || voice.gain <= 0
              || voice.position.distanceTo(loops.get(i).audible) >= .002) return false;
        }
        check(voices.stream().map(Source::id).distinct().count() == SOURCE_COUNT,
            "Independent sounds must each own exactly one distinct native channel");
        routed = voices;
        passed.add("audio/simultaneous local and routed sources retain distinct native channels and positions");
        changeObstacle(true);
        next(Step.OBSTRUCT);
      }
      case OBSTRUCT -> {
        if (!obstacleReady(true)) return false;
        next(Step.OBSCURED);
      }
      case OBSCURED -> {
        if (!observe()) return false;
        var voices = observation.join(); observation = null;
        for (int i = 0; i < voices.size(); i++) {
          var voice = voices.get(i);
          var original = routed.get(i);
          if (loops.get(i).routed && voice.gain > original.gain * .75) return false;
          check(voice.id == original.id && voice.state == AL10.AL_PLAYING && voice.gain > 0
              && voice.position.distanceTo(original.position) < .002,
              "Occlusion must preserve the channel, audible position and physical distance of source " + i);
          if (!loops.get(i).routed)
            check(Math.abs(voice.gain - original.gain) < original.gain * .05,
                "Remote cover changed independent local source " + i);
        }
        obscured = voices;
        changeObstacle(false);
        next(Step.CLEAR);
      }
      case CLEAR -> {
        if (!obstacleReady(false)) return false;
        next(Step.RECOVERED);
      }
      case RECOVERED -> {
        if (!observe()) return false;
        var voices = observation.join(); observation = null;
        for (int i = 0; i < voices.size(); i++) {
          if (voices.get(i).gain < routed.get(i).gain * .95) return false;
          check(voices.get(i).id == routed.get(i).id
              && voices.get(i).position.distanceTo(routed.get(i).position) < .002,
              "Removing cover must restore the existing channel without moving source " + i);
        }
        passed.add("audio/real block cover attenuates and restores remote transmission independently of distance");
        client.getSoundManager().pauseAllExcept();
        next(Step.PAUSE);
      }
      case PAUSE -> {
        if (!observe()) return false;
        var voices = observation.join(); observation = null;
        if (voices.stream().anyMatch(voice -> voice.state != AL10.AL_PAUSED)) return false;
        client.getSoundManager().resume();
        next(Step.RESUME);
      }
      case RESUME -> {
        if (!observe()) return false;
        var voices = observation.join(); observation = null;
        if (voices.stream().anyMatch(voice -> voice.state != AL10.AL_PLAYING)) return false;
        passed.add("audio/simultaneous positional channels pause and resume normally");
        client.getSoundManager().stop(loops.get(1).sound);
        next(Step.STOP_ONE);
      }
      case STOP_ONE -> {
        if (client.getSoundManager().isActive(loops.get(1).sound) || !handles.get(1).isStopped()) return false;
        if (!observe()) return false;
        var voices = observation.join(); observation = null;
        for (int i = 0; i < voices.size(); i++) {
          if (i == 1) continue;
          var voice = voices.get(i);
          check(voice.id == routed.get(i).id && voice.state == AL10.AL_PLAYING && voice.gain > 0,
              "Stopping one sound must preserve every other native channel, source " + i);
        }
        passed.add("audio/stopping one source releases only its own native channel");
        mutation = originalServer.submit(fixture::clearPortals);
        next(Step.CLOSE);
      }
      case CLOSE -> {
        if (!mutation.isDone()) return false;
        mutation.join();
        if (PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(fixture.gunId()))) return false;
        next(Step.DIRECT);
      }
      case DIRECT -> {
        if (!observe()) return false;
        var voices = observation.join(); observation = null;
        for (int i = 0; i < voices.size(); i++) {
          if (i == 1) continue;
          var voice = voices.get(i);
          check(voice.id == routed.get(i).id && voice.state == AL10.AL_PLAYING && voice.gain > 0
              && voice.position.distanceTo(loops.get(i).emitter) < .002,
              "Closing the portal must preserve the native channel at original emitter " + i + ": " + voice);
        }
        direct = voices;
        passed.add("audio/portal closure removes routed paths and preserves original channels");
        next(Step.AFTER);
      }
      case AFTER -> {
        if (screenshot == null) { screenshot = frames.checkpoint("audio-closed-route"); return false; }
        if (!screenshot.isDone()) return false;
        var image = screenshot.join();
        var region = image.project(arrangement.imageBounds(arrangement.markers().getFirst().bounds())).orElseThrow();
        image.assertMaterial(region, FrameCapture.Material.RED, 0, .01);
        check(image.difference(before, region, 12).changedFraction() > .1,
            "The same actual window must show the solid wall after the audible portal route closes");
        loops.forEach(loop -> client.getSoundManager().stop(loop.sound));
        next(Step.STOP);
      }
      case STOP -> {
        if (loops.stream().anyMatch(loop -> client.getSoundManager().isActive(loop.sound))
            || handles.stream().anyMatch(handle -> !handle.isStopped())) return false;
        writeProof();
        passed.add("audio/normal sound stop releases all actual native channels without leaking loops");
        next(Step.DONE);
      }
      default -> { return true; }
    }
    return false;
  }

  private void changeObstacle(boolean solid) {
    mutation = originalServer.submit(() -> obstacle.forEach(position ->
        fixture.block(position, (solid ? Blocks.QUARTZ_BLOCK : Blocks.AIR).defaultBlockState())));
  }

  private boolean obstacleReady(boolean solid) {
    if (!mutation.isDone()) return false;
    mutation.join();
    return obstacle.stream().allMatch(position -> client.level.getBlockState(position)
        .is(solid ? Blocks.QUARTZ_BLOCK : Blocks.AIR));
  }

  private boolean observe() {
    if (observation == null) {
      if (handles == null) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).portalix$engine();
        var channels = ((SoundEngineAccessor) engine).portalix$channels();
        var current = loops.stream().map(loop -> channels.get(loop.sound)).toList();
        if (current.stream().anyMatch(handle -> handle == null)) return false;
        handles = current;
      }
      var pending = new ArrayList<CompletableFuture<Source>>();
      for (int i = 0; i < handles.size(); i++) {
        var handle = handles.get(i);
        pending.add(handle.isStopped() ? CompletableFuture.completedFuture(null) : inspect(handle, loops.get(i).sound));
      }
      observation = CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
          .thenApply(ignored -> pending.stream().map(CompletableFuture::join).toList());
      return false;
    }
    return observation.isDone();
  }

  private static CompletableFuture<Source> inspect(ChannelAccess.ChannelHandle handle, SourcePositionalSound sound) {
    var result = new CompletableFuture<Source>();
    handle.execute(channel -> {
      try {
        var access = (PortalAudioChannelAccess) channel;
        var voice = access.portalix$audioVoice();
        check(voice != null && voice.sound() == sound, "Native sound channel did not bind the expected audio voice");
        var source = source(access.portalix$source());
        check(AL10.alGetError() == AL10.AL_NO_ERROR, "OpenAL failed while inspecting the real positional sound");
        result.complete(source);
      } catch (Throwable failure) { result.completeExceptionally(failure); }
    });
    return result;
  }

  private static Source source(int id) {
    float[] position = new float[3];
    AL10.alGetSourcefv(id, AL10.AL_POSITION, position);
    return new Source(id, AL10.alGetSourcei(id, AL10.AL_SOURCE_STATE),
        AL10.alGetSourcef(id, AL10.AL_GAIN), new Vec3(position[0], position[1], position[2]));
  }

  private void writeProof() {
    try {
      Files.createDirectories(frames.directory());
      Files.writeString(frames.directory().resolve("audio-proof.json"), new GsonBuilder().setPrettyPrinting().create()
          .toJson(java.util.Map.of("routed", routed, "obscured", obscured, "closed", direct,
              "nativeChannelsStopped", handles.stream().allMatch(ChannelAccess.ChannelHandle::isStopped))));
    } catch (IOException failure) { throw new UncheckedIOException(failure); }
  }

  private void next(Step next) { step = next; age = 0; screenshot = null; }
  public List<String> passed() { return List.copyOf(passed); }

  @Override public CompletableFuture<Void> cleanup() {
    client.getSoundManager().resume();
    loops.forEach(loop -> client.getSoundManager().stop(loop.sound));
    return super.cleanup();
  }
}

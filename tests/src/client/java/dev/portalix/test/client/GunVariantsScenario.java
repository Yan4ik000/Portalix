package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import com.google.gson.GsonBuilder;
import dev.portalix.client.PortalixClient;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.test.WorldScenarioFixture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

final class GunVariantsScenario {
  private enum Step { PREPARE_VIEW, EMPTY_HAND, HOLSTER, EQUIP, CAPTURE, RESTORE_HOLSTER, RESTORE, RESTORED, REPORT, RESTORE_VIEW, DONE }

  private static final List<PortalixVariant> VARIANTS = List.of(PortalixVariant.STANDARD,
      PortalixVariant.BLUE, PortalixVariant.ORANGE, PortalixVariant.POTATO);
  private static final List<String> GROUPS = List.of(
      "visual/all four real gun variants synchronize, draw and retain a textured first-person body",
      "visual/shared gun shell pixels change from neutral to blue to orange without changing the world",
      "visual/potato variant adds a localized warm mesh and the original gun restores afterward");
  private static final Component CAPTION = Component.literal("Gun variants: stationary texture and geometry comparison");
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final WorldScenarioFixture fixture;
  private final ScenarioInput input;
  private final Vec3 position;
  private final Vec3 floorAim;
  private final Vec3 originalAim;
  private final PortalixVariant original;
  private final Map<PortalixVariant, FrameCapture.Frame> images = new EnumMap<>(PortalixVariant.class);
  private final Map<String, Object> proof = new LinkedHashMap<>();
  private final Map<String, Object> frameEvidence = new LinkedHashMap<>();
  private final List<Map<String, Object>> comparisons = new ArrayList<>();
  private Step step = Step.PREPARE_VIEW;
  private int age = -1;
  private int variant;
  private int equipTick;
  private FrameCapture.Frame emptyHand;
  private CompletableFuture<Void> mutation;
  private CompletableFuture<FrameCapture.Frame> screenshot;
  private CompletableFuture<Void> report;

  GunVariantsScenario(Minecraft client, ScenarioFrames frames, WorldScenarioFixture fixture) {
    this.client = client;
    this.frames = frames;
    this.fixture = fixture;
    input = new ScenarioInput(client);
    position = client.player.position();
    originalAim = client.player.getEyePosition().add(client.player.getLookAngle().scale(5));
    floorAim = client.player.getEyePosition().add(5, -2, 0);
    check(client.options.getCameraType() == CameraType.FIRST_PERSON && !client.gui.hud.isHidden(),
        "Gun variants require the normal visible first-person camera");
    check(client.player.getInventory().getSelectedSlot() == 0
        && fixture.gunId().equals(PortalixIdentity.get(client.player.getMainHandItem())),
        "Gun variants require the actual course gun selected in its assigned hotbar slot");
    original = PortalixVariant.of(client.player.getMainHandItem());
    proof.put("gunId", fixture.gunId().toString());
    proof.put("frames", frameEvidence);
    proof.put("pairedComparisons", comparisons);
    proof.put("viewPreparation", Map.of("playerPosition", List.of(position.x, position.y, position.z),
        "floorAim", List.of(floorAim.x, floorAim.y, floorAim.z)));
  }

  String title() {
    return "Gun variants: " + step.name().toLowerCase(java.util.Locale.ROOT)
        + (step == Step.EQUIP || step == Step.CAPTURE ? " " + VARIANTS.get(variant).name().toLowerCase(java.util.Locale.ROOT) : "");
  }

  List<String> passed() { return step == Step.DONE ? GROUPS : List.of(); }

  boolean tick() {
    if (step == Step.DONE) return true;
    check(++age < 400, "Gun variant scenario timed out during " + title());
    check(client.player.position().distanceTo(position) < .05, "Gun variant evidence must not reposition the player");
    check(client.options.getCameraType() == CameraType.FIRST_PERSON && !client.gui.hud.isHidden(),
        "Gun variant evidence lost the real first-person hand view");
    // Identical text prevents a changed stage caption from contributing to paired hand differences.
    client.gui.hud.setOverlayMessage(CAPTION, false);
    switch (step) {
      case PREPARE_VIEW -> {
        if (!frames.pause()) return false;
        if (age == 0) input.stop();
        if (!input.lookAt(floorAim)) return false;
        frames.monitor(true);
        next(Step.EMPTY_HAND);
      }
      case EMPTY_HAND -> {
        if (age == 0) { input.stop(); input.slot(2); }
        if (age < 45 || !PortalixClient.displayedGun().isEmpty()) return false;
        if (capture("empty-hand", () -> client.player.getMainHandItem().isEmpty() && PortalixClient.displayedGun().isEmpty(), frame -> {
          emptyHand = frame;
          recordFrame("empty-hand", frame);
          proof.put("handRegion", handRegion(frame));
        })) next(Step.HOLSTER);
      }
      case HOLSTER -> {
        if (age == 0) {
          PortalixVariant expected = VARIANTS.get(variant);
          mutation = client.getSingleplayerServer().submit(() -> fixture.equipVariant(expected));
          input.slot(2);
        }
        if (!mutation.isDone()) return false;
        mutation.join();
        if (age < 40 || !PortalixClient.displayedGun().isEmpty()) return false;
        next(Step.EQUIP);
      }
      case EQUIP -> {
        if (age == 0) { input.slot(0); equipTick = client.player.tickCount; }
        if (age < 50 || !GunEquipReadiness.ready(client, fixture.gunId(), VARIANTS.get(variant))) return false;
        next(Step.CAPTURE);
      }
      case CAPTURE -> {
        PortalixVariant current = VARIANTS.get(variant);
        if (capture(current.name().toLowerCase(java.util.Locale.ROOT),
            () -> GunEquipReadiness.ready(client, fixture.gunId(), current), frame -> {
          paired(frame, emptyHand);
          FrameCapture.Difference foreground = frame.difference(emptyHand, handRegion(frame), 24);
          check(foreground.changedFraction() > .02 && foreground.changedPixels() > 300,
              "The actual " + current + " gun did not appear in the first-person hand image: " + foreground);
          check(frame.statistics(handRegion(frame), pixel -> true).luminanceDeviation() > 15,
              "The actual " + current + " gun has no textured foreground");
          images.put(current, frame);
          recordFrame(current.name().toLowerCase(java.util.Locale.ROOT), frame);
          proof.put(current.name().toLowerCase(java.util.Locale.ROOT) + "Foreground", foreground);
          if (current == PortalixVariant.ORANGE) assertShellPalette();
          if (current == PortalixVariant.POTATO) assertPotato();
        })) {
          if (++variant == VARIANTS.size()) next(Step.RESTORE_HOLSTER);
          else next(Step.HOLSTER);
        }
      }
      case RESTORE_HOLSTER -> {
        if (age == 0) {
          mutation = client.getSingleplayerServer().submit(() -> fixture.equipVariant(original));
          input.slot(2);
        }
        if (!mutation.isDone()) return false;
        mutation.join();
        if (age < 40 || !PortalixClient.displayedGun().isEmpty()) return false;
        next(Step.RESTORE);
      }
      case RESTORE -> {
        if (age == 0) { input.slot(0); equipTick = client.player.tickCount; }
        if (age < 50 || !GunEquipReadiness.ready(client, fixture.gunId(), original)) return false;
        next(Step.RESTORED);
      }
      case RESTORED -> {
        if (capture("restored", () -> GunEquipReadiness.ready(client, fixture.gunId(), original), frame -> {
          FrameCapture.Frame reference = images.get(original);
          paired(frame, reference);
          FrameCapture.Difference difference = frame.difference(reference, handRegion(frame), 24);
          check(difference.changedFraction() < .04,
              "Restoring the original variant did not restore its rendered model and skin: " + difference);
          proof.put("restoredVariant", original.name());
          proof.put("restoredHandDifference", difference);
          recordFrame("restored", frame);
          report = CompletableFuture.runAsync(() -> {
            try {
              Files.writeString(frame.png().getParent().resolve("gun-variants-proof.json"),
                  new GsonBuilder().setPrettyPrinting().create().toJson(proof));
            } catch (IOException failure) {
              throw new UncheckedIOException(failure);
            }
          }, Util.ioPool());
        })) next(Step.REPORT);
      }
      case REPORT -> {
        if (!report.isDone()) return false;
        report.join();
        next(Step.RESTORE_VIEW);
      }
      case RESTORE_VIEW -> {
        if (!input.lookAt(originalAim)) return false;
        next(Step.DONE);
      }
      default -> throw new AssertionError("Unexpected gun variant step " + step);
    }
    return step == Step.DONE;
  }

  private void assertShellPalette() {
    var standard = images.get(PortalixVariant.STANDARD);
    var blue = images.get(PortalixVariant.BLUE);
    var orange = images.get(PortalixVariant.ORANGE);
    var region = handRegion(standard);
    Extent stripes = new Extent(), commonShell = new Extent();
    for (int y = region.y(); y < region.y() + region.height(); y++)
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        int neutral = standard.pixel(x, y), blueSkin = blue.pixel(x, y), orangeSkin = orange.pixel(x, y);
        if (!neutralShell(neutral) || channelDifference(neutral, emptyHand.pixel(x, y)) < 24) continue;
        // Authored assets put the blue and orange stripe on the same white shell UV regions.
        if (FrameCapture.Material.BLUE.test(blueSkin) && FrameCapture.Material.ORANGE.test(orangeSkin)
            && channelDifference(neutral, blueSkin) >= 24 && channelDifference(neutral, orangeSkin) >= 24)
          stripes.add(x, y);
        if (neutralShell(blueSkin) && neutralShell(orangeSkin)
            && channelDifference(blueSkin, emptyHand.pixel(x, y)) >= 24
            && channelDifference(orangeSkin, emptyHand.pixel(x, y)) >= 24) commonShell.add(x, y);
      }
    check(stripes.count >= 20 && stripes.width() >= 5 && stripes.height() >= 3,
        "The same gun shell pixels did not change from neutral to blue to orange: " + stripes.metadata());
    check(commonShell.count >= 200 && commonShell.width() >= 12 && commonShell.height() >= 12,
        "Changing the gun palette lost the shared neutral shell: " + commonShell.metadata());
    proof.put("orderedNeutralBlueOrangeStripe", stripes.metadata());
    proof.put("commonWhiteShell", commonShell.metadata());
  }

  private void assertPotato() {
    var standard = images.get(PortalixVariant.STANDARD);
    var potato = images.get(PortalixVariant.POTATO);
    var region = handRegion(potato);
    Extent addition = new Extent(), shell = new Extent();
    for (int y = region.y(); y < region.y() + region.height(); y++)
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        int plain = standard.pixel(x, y), current = potato.pixel(x, y), background = emptyHand.pixel(x, y);
        if (warmPotato(current) && channelDifference(current, plain) >= 24
            && channelDifference(plain, background) <= 12 && channelDifference(current, background) >= 24)
          addition.add(x, y);
        if (neutralShell(plain) && neutralShell(current) && channelDifference(plain, emptyHand.pixel(x, y)) >= 24
            && channelDifference(current, emptyHand.pixel(x, y)) >= 24) shell.add(x, y);
      }
    check(addition.count >= 20 && addition.width() >= 4 && addition.height() >= 4,
        "The potato variant did not add a visible warm textured mesh: " + addition.metadata());
    check((long) addition.width() * addition.height() < region.area() * .35,
        "The potato change covers the scene instead of a localized mesh: " + addition.metadata());
    check(shell.count >= 200, "Adding the potato lost the common gun shell: " + shell.metadata());
    proof.put("potatoWarmMeshAddition", addition.metadata());
    proof.put("potatoSharedWhiteShell", shell.metadata());
    proof.put("potatoForegroundOwnership", Map.of("backgroundReferenceFrameId", emptyHand.id(),
        "standardReferenceFrameId", standard.id(), "standardBackgroundMaximumChannelDifference", 12,
        "potatoBackgroundMinimumChannelDifference", 24, "potatoStandardMinimumChannelDifference", 24));
  }

  private void paired(FrameCapture.Frame frame, FrameCapture.Frame baseline) {
    double cameraDistance = frame.cameraPosition().distanceTo(baseline.cameraPosition());
    double pitchDelta = Math.abs(frame.cameraPitch() - baseline.cameraPitch());
    double yawDelta = Math.abs(Mth.wrapDegrees(frame.cameraYaw() - baseline.cameraYaw()));
    check(cameraDistance < .002 && pitchDelta < .05 && yawDelta < .05,
        "Gun variant frames used different actual camera poses");
    var control = new FrameCapture.Region(frame.width() / 12, frame.height() * 3 / 4,
        frame.width() / 8, frame.height() / 10);
    var difference = frame.difference(baseline, control, 24);
    check(difference.changedFraction() < .03 && difference.meanChannelDifference() < 5,
        "Gun variant frames include an unstable background control: " + difference);
    comparisons.add(Map.of("frameId", frame.id(), "referenceFrameId", baseline.id(),
        "cameraDistance", cameraDistance, "pitchDelta", pitchDelta, "yawDelta", yawDelta,
        "controlRegion", control, "controlDifference", difference));
  }

  private void recordFrame(String name, FrameCapture.Frame frame) {
    Map<String, Object> metadata = new LinkedHashMap<>(frame.metadata());
    metadata.put("image", frame.png().getFileName().toString());
    metadata.put("inventoryItem", BuiltInRegistries.ITEM.getKey(client.player.getMainHandItem().getItem()).toString());
    metadata.put("displayedItem", BuiltInRegistries.ITEM.getKey(PortalixClient.displayedGun().getItem()).toString());
    if (!"empty-hand".equals(name)) metadata.put("ticksSinceEquipInput", frame.playerTick() - equipTick);
    frameEvidence.put(name, metadata);
  }

  private boolean capture(String suffix, BooleanSupplier ready, Consumer<FrameCapture.Frame> assertion) {
    if (screenshot == null) {
      screenshot = frames.reservedCheckpoint("gun-variants-" + suffix, ready);
      return false;
    }
    if (!screenshot.isDone()) return false;
    var frame = screenshot.join();
    screenshot = null;
    assertion.accept(frame);
    return true;
  }

  private void next(Step next) {
    check(screenshot == null, "Gun variant transition has an unfinished frame capture");
    input.stop();
    step = next;
    age = -1;
  }

  private static FrameCapture.Region handRegion(FrameCapture.Frame frame) {
    return new FrameCapture.Region(frame.width() / 2, frame.height() / 2,
        frame.width() * 9 / 20, frame.height() * 2 / 5);
  }

  private static boolean neutralShell(int color) {
    int r = color >>> 16 & 255, g = color >>> 8 & 255, b = color & 255;
    int maximum = Math.max(r, Math.max(g, b)), minimum = Math.min(r, Math.min(g, b));
    return minimum >= 45 && maximum - minimum <= maximum * .25 + 10;
  }

  private static boolean warmPotato(int color) {
    int r = color >>> 16 & 255, g = color >>> 8 & 255, b = color & 255;
    return r >= 45 && g >= 25 && r > g * 1.08 && r < g * 1.7 && g > b * 1.15;
  }

  private static int channelDifference(int first, int second) {
    return Math.max(Math.abs((first >>> 16 & 255) - (second >>> 16 & 255)),
        Math.max(Math.abs((first >>> 8 & 255) - (second >>> 8 & 255)), Math.abs((first & 255) - (second & 255))));
  }

  private static final class Extent {
    int count;
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
    void add(int x, int y) {
      count++; minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
    }
    int width() { return count == 0 ? 0 : maxX - minX + 1; }
    int height() { return count == 0 ? 0 : maxY - minY + 1; }
    Map<String, Object> metadata() {
      return Map.of("pixels", count, "width", width(), "height", height(), "bounds",
          count == 0 ? List.of() : List.of(minX, minY, maxX + 1, maxY + 1));
    }
  }
}

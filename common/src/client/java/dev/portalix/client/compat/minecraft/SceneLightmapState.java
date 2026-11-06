package dev.portalix.client.compat.minecraft;

import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;

final class SceneLightmapState {
  private float skyFactor;
  private float blockFactor;
  private float nightVisionEffectIntensity;
  private float darknessEffectScale;
  private float bossOverlayWorldDarkening;
  private float brightness;
  private final Vector3f blockLightTint = new Vector3f();
  private final Vector3f skyLightColor = new Vector3f();
  private final Vector3f ambientColor = new Vector3f();
  private final Vector3f nightVisionColor = new Vector3f();
  private boolean initialized;

  public boolean update(LightmapRenderState state) {
    if (!state.needsUpdate) return false;
    if (initialized
        && skyFactor == state.skyFactor
        && blockFactor == state.blockFactor
        && nightVisionEffectIntensity == state.nightVisionEffectIntensity
        && darknessEffectScale == state.darknessEffectScale
        && bossOverlayWorldDarkening == state.bossOverlayWorldDarkening
        && brightness == state.brightness
        && blockLightTint.equals(state.blockLightTint)
        && skyLightColor.equals(state.skyLightColor)
        && ambientColor.equals(state.ambientColor)
        && nightVisionColor.equals(state.nightVisionColor)) return false;
    skyFactor = state.skyFactor;
    blockFactor = state.blockFactor;
    nightVisionEffectIntensity = state.nightVisionEffectIntensity;
    darknessEffectScale = state.darknessEffectScale;
    bossOverlayWorldDarkening = state.bossOverlayWorldDarkening;
    brightness = state.brightness;
    blockLightTint.set(state.blockLightTint);
    skyLightColor.set(state.skyLightColor);
    ambientColor.set(state.ambientColor);
    nightVisionColor.set(state.nightVisionColor);
    initialized = true;
    return true;
  }
}

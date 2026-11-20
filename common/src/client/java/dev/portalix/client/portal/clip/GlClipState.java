package dev.portalix.client.portal.clip;

import org.lwjgl.opengl.GL33C;

public final class GlClipState {
  private static boolean enabled;

  private GlClipState() {}

  public static void setEnabled(boolean value) {
    if (enabled == value) return;
    if (value) {
      GL33C.glEnable(GL33C.GL_CLIP_DISTANCE0);
      GL33C.glEnable(GL33C.GL_CLIP_DISTANCE1);
    } else {
      GL33C.glDisable(GL33C.GL_CLIP_DISTANCE0);
      GL33C.glDisable(GL33C.GL_CLIP_DISTANCE1);
    }
    enabled = value;
  }
}

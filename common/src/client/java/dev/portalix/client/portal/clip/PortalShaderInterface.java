package dev.portalix.client.portal.clip;

public record PortalShaderInterface(
    boolean projected, boolean terrain, String vertexCorrection, boolean homogeneous) {
  public PortalShaderInterface(boolean projected, boolean terrain, String vertexCorrection) {
    this(projected, terrain, vertexCorrection, false);
  }
}

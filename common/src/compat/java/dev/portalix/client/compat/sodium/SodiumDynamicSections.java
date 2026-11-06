package dev.portalix.client.compat.sodium;

public interface SodiumDynamicSections {
  long[] portalix$dynamicSections();

  void portalix$dynamicSection(int sectionIndex, boolean dynamic);
}

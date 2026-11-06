package dev.portalix.client.compat;

public interface RenderScope extends AutoCloseable {
  @Override
  void close();
}

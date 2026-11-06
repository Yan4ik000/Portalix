package dev.portalix.test.client;

public final class ClientAssertions {
  private ClientAssertions() {}

  public static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}

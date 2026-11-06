package dev.portalix.portal;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortalRoutesTest {
  private static final PortalLink A = link(new UUID(1, 2), 3, 4, 5);
  private static final PortalLink B = link(new UUID(6, 7), 8, 9, 10);

  @Test
  void identityMatchingDoesNotDependOnTheDecodedGeometryRecord() {
    var copy = new PortalLink(A.gun(), A.generation(),
        new PortalInstance(A.entrance().endpoint(), A.entrance().bornTick() + 1,
            A.entrance().identity(), A.entrance().variant()), A.exit());
    assertNotEquals(A, copy);
    assertTrue(PortalRoutes.matches(List.of(A, B), List.of(copy, B)));
    assertEquals(List.of(), PortalRoutes.simplify(List.of(A, copy.inverse())));
  }

  @Test
  void onlyAdjacentInverseIdentitiesCancel() {
    var replacement = new PortalLink(A.gun(), A.generation() + 1, A.entrance(), A.exit());
    assertEquals(List.of(A, B, A.inverse()), PortalRoutes.simplify(List.of(A, B, A.inverse())));
    assertEquals(List.of(A, replacement.inverse()), PortalRoutes.simplify(List.of(A, replacement.inverse())));
    assertFalse(PortalRoutes.matches(List.of(A, B), List.of(B, A)));
    assertFalse(PortalRoutes.matches(List.of(A), List.of(A.inverse())));
    assertFalse(PortalRoutes.matches(List.of(A), List.of(replacement)));
  }

  @Test
  void currentResolutionRejectsClosedAndReplacedGenerations() {
    var replacement = new PortalLink(A.gun(), A.generation() + 1, A.entrance(), A.exit());
    assertSame(A, PortalRoutes.resolve(A, List.of(replacement, A)));
    assertNull(PortalRoutes.resolve(A, List.of(replacement)));
    assertNull(PortalRoutes.resolve(A, List.of(A.inverse())));
    assertNull(PortalRoutes.resolve(A, List.of()));
  }

  @Test
  void aCanonicalPathOwnsOnlyItsRemainingValues() {
    var input = new ArrayList<>(List.of(A, B, B.inverse()));
    var path = PortalRoutes.simplify(input);
    input.clear();
    assertEquals(List.of(A), path);
    assertSame(A, path.getFirst());
    assertThrows(UnsupportedOperationException.class, path::clear);
  }
  @Test
  void routeReductionCancelsOnlyMatchingAdjacentInverseCrossings() {
    var a = new PortalLinkId(new UUID(1, 2), 3, 4, 5);
    var b = new PortalLinkId(new UUID(6, 7), 8, 9, 10);
    var staleA = new PortalLinkId(a.gun(), a.generation() + 1, a.exit(), a.entrance());
    assertEquals(List.of(), PortalRoutes.simplifyIds(List.of(a, b, b.inverse(), a.inverse())));
    assertEquals(List.of(a, staleA), PortalRoutes.simplifyIds(List.of(a, staleA)));
    assertEquals(List.of(a, b, a.inverse()), PortalRoutes.simplifyIds(List.of(a, b, a.inverse())));
    var longRoute = new ArrayList<PortalLinkId>();
    for (int i = 0; i < 100; i++) longRoute.add(i % 2 == 0 ? a : a.inverse());
    assertTrue(PortalRoutes.simplifyIds(longRoute).isEmpty());
  }
}

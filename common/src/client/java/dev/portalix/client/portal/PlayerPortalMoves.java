package dev.portalix.client.portal;

import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalLink;
import java.util.ArrayList;
import java.util.List;

final class PlayerPortalMoves {
  private record Move(long sequence, List<PortalLink> crossings) {}

  private final List<Move> pending = new ArrayList<>();
  private List<PortalLink> correction;

  void predicted(long sequence, List<PortalLink> crossings) {
    if (!crossings.isEmpty()) pending.add(new Move(sequence, List.copyOf(crossings)));
  }

  List<PortalLink> ownerRoute(long completed, List<PortalLink> active) {
    var route = new ArrayList<PortalLink>();
    for (var move : pending.reversed()) {
      if (move.sequence() > completed) appendInverse(route, move.crossings());
    }
    route.addAll(active);
    return PortalRoutes.simplify(route);
  }

  List<PortalLink> acknowledged(long sequence, boolean accepted, List<PortalLink> actual) {
    if (!accepted) {
      correction = List.copyOf(actual);
      return List.of();
    }
    var predicted = new ArrayList<PortalLink>();
    for (var move : pending) {
      if (move.sequence() <= sequence) predicted.addAll(move.crossings());
    }
    var changes = new ArrayList<PortalLink>();
    if (!PortalRoutes.matches(predicted, actual)) {
      undoPending(changes);
      changes.addAll(actual);
      for (var move : pending) {
        if (move.sequence() > sequence) changes.addAll(move.crossings());
      }
    }
    pending.removeIf(move -> move.sequence() <= sequence);
    return List.copyOf(changes);
  }

  List<PortalLink> corrected() {
    var changes = new ArrayList<PortalLink>();
    undoPending(changes);
    if (correction != null) changes.addAll(correction);
    clear();
    return List.copyOf(changes);
  }

  void clear() {
    pending.clear();
    correction = null;
  }

  private void undoPending(List<PortalLink> changes) {
    for (var move : pending.reversed()) appendInverse(changes, move.crossings());
  }

  private static void appendInverse(List<PortalLink> target, List<PortalLink> route) {
    for (var crossing : route.reversed()) target.add(crossing.inverse());
  }
}

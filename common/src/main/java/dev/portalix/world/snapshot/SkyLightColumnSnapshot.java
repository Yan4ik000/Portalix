package dev.portalix.world.snapshot;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public final class SkyLightColumnSnapshot {
  private static final int BITS = 5;
  private static final int WIDTH = 1 << BITS;
  private Node root;
  private Object owner = new Object();
  private int defaultValue;

  private abstract static class Node {
    final Object owner;

    Node(Object owner) {
      this.owner = owner;
    }
  }

  private static final class Branch extends Node {
    final Node[] children;
    int count;

    Branch(Object owner) {
      super(owner);
      children = new Node[WIDTH];
    }

    Branch(Object owner, Branch source) {
      super(owner);
      children = source.children.clone();
      count = source.count;
    }
  }

  private static final class Leaf extends Node {
    final Long2IntOpenHashMap columns;

    Leaf(Object owner) {
      super(owner);
      columns = new Long2IntOpenHashMap();
    }

    Leaf(Object owner, Leaf source) {
      super(owner);
      columns = source.columns.clone();
    }
  }

  public SkyLightColumnSnapshot(Long2IntOpenHashMap columns) {
    defaultValue = columns.defaultReturnValue();
    var entries = columns.long2IntEntrySet().fastIterator();
    while (entries.hasNext()) {
      var entry = entries.next();
      put(entry.getLongKey(), entry.getIntValue());
    }
  }

  private SkyLightColumnSnapshot(Node root, int defaultValue) {
    this.root = root;
    this.defaultValue = defaultValue;
  }

  public SkyLightColumnSnapshot copy() {
    owner = new Object();
    return new SkyLightColumnSnapshot(root, defaultValue);
  }

  private Leaf leaf(long column) {
    long hash = HashCommon.mix(column);
    Node node = root;
    int shift = 0;
    while (node instanceof Branch branch) {
      node = branch.children[(int) (hash >>> shift) & (WIDTH - 1)];
      shift += BITS;
    }
    return (Leaf) node;
  }

  public int get(long column) {
    var leaf = leaf(column);
    return leaf == null ? defaultValue : leaf.columns.getOrDefault(column, defaultValue);
  }

  public int put(long column, int top) {
    var leaf = leaf(column);
    int previous = get(column);
    if (leaf == null || !leaf.columns.containsKey(column) || previous != top)
      root = update(root, column, HashCommon.mix(column), 0, top, false);
    return previous;
  }

  public int remove(long column) {
    var leaf = leaf(column);
    if (leaf == null || !leaf.columns.containsKey(column)) return defaultValue;
    int previous = leaf.columns.get(column);
    root = update(root, column, HashCommon.mix(column), 0, 0, true);
    return previous;
  }

  public void defaultReturnValue(int value) {
    defaultValue = value;
  }

  private Node update(Node node, long column, long hash, int shift, int top, boolean remove) {
    if (node == null) node = new Leaf(owner);
    if (node instanceof Leaf leaf) {
      if (!remove && leaf.columns.size() == WIDTH && !leaf.columns.containsKey(column)) {
        node = new Branch(owner);
        var entries = leaf.columns.long2IntEntrySet().fastIterator();
        while (entries.hasNext()) {
          var entry = entries.next();
          node =
              update(
                  node,
                  entry.getLongKey(),
                  HashCommon.mix(entry.getLongKey()),
                  shift,
                  entry.getIntValue(),
                  false);
        }
      } else {
        if (leaf.owner != owner) leaf = new Leaf(owner, leaf);
        if (remove) leaf.columns.remove(column);
        else leaf.columns.put(column, top);
        return leaf.columns.isEmpty() ? null : leaf;
      }
    }
    var branch = (Branch) node;
    if (branch.owner != owner) branch = new Branch(owner, branch);
    int index = (int) (hash >>> shift) & (WIDTH - 1);
    Node previous = branch.children[index];
    Node next = update(previous, column, hash, shift + BITS, top, remove);
    branch.children[index] = next;
    if (previous == null && next != null) branch.count++;
    else if (previous != null && next == null) branch.count--;
    return branch.count == 0 ? null : branch;
  }
}

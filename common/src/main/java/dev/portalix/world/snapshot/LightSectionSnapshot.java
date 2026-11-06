package dev.portalix.world.snapshot;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;

public final class LightSectionSnapshot {
  private static final int BITS = 5;
  private static final int WIDTH = 1 << BITS;
  private Node root;
  private Object owner = new Object();

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
    final Long2ObjectOpenHashMap<Column> columns;

    Leaf(Object owner) {
      super(owner);
      columns = new Long2ObjectOpenHashMap<>();
    }

    Leaf(Object owner, Leaf source) {
      super(owner);
      columns = source.columns.clone();
    }
  }

  private static final class Column {
    final Object owner;
    final DataLayer[] layers;
    int count;

    Column(Object owner) {
      this.owner = owner;
      layers = new DataLayer[WIDTH];
    }

    Column(Object owner, Column source) {
      this.owner = owner;
      layers = source.layers.clone();
      count = source.count;
    }
  }

  public LightSectionSnapshot(Long2ObjectOpenHashMap<DataLayer> layers) {
    var entries = layers.long2ObjectEntrySet().fastIterator();
    while (entries.hasNext()) {
      var entry = entries.next();
      put(entry.getLongKey(), entry.getValue());
    }
  }

  private LightSectionSnapshot(Node root) {
    this.root = root;
  }

  public LightSectionSnapshot copy() {
    owner = new Object();
    return new LightSectionSnapshot(root);
  }

  public DataLayer get(long section) {
    var column = column(section & -WIDTH);
    return column == null ? null : column.layers[(int) section & (WIDTH - 1)];
  }

  private Column column(long position) {
    long hash = HashCommon.mix(position);
    Node node = root;
    int shift = 0;
    while (node instanceof Branch branch) {
      node = branch.children[(int) (hash >>> shift) & (WIDTH - 1)];
      shift += BITS;
    }
    return node == null ? null : ((Leaf) node).columns.get(position);
  }

  public boolean contains(long section) {
    return get(section) != null;
  }

  public DataLayer put(long section, DataLayer layer) {
    long position = section & -WIDTH;
    int index = (int) section & (WIDTH - 1);
    var previousColumn = column(position);
    var previous = previousColumn == null ? null : previousColumn.layers[index];
    if (previous == layer) return previous;
    var column = previousColumn;
    if (column == null) column = new Column(owner);
    else if (column.owner != owner) column = new Column(owner, column);
    column.layers[index] = layer;
    if (previous == null) column.count++;
    else if (layer == null) column.count--;
    if (column.count == 0) column = null;
    if (column != previousColumn)
      root = update(root, position, HashCommon.mix(position), 0, column);
    return previous;
  }

  public DataLayer remove(long section) {
    return put(section, null);
  }

  private Node update(Node node, long position, long hash, int shift, Column column) {
    if (node == null) node = new Leaf(owner);
    if (node instanceof Leaf leaf) {
      if (column != null && leaf.columns.size() == WIDTH && !leaf.columns.containsKey(position)) {

        node = new Branch(owner);
        var entries = leaf.columns.long2ObjectEntrySet().fastIterator();
        while (entries.hasNext()) {
          var entry = entries.next();
          node =
              update(
                  node,
                  entry.getLongKey(),
                  HashCommon.mix(entry.getLongKey()),
                  shift,
                  entry.getValue());
        }
      } else {
        if (leaf.owner != owner) leaf = new Leaf(owner, leaf);
        if (column == null) leaf.columns.remove(position);
        else leaf.columns.put(position, column);
        return leaf.columns.isEmpty() ? null : leaf;
      }
    }
    var branch = (Branch) node;
    if (branch.owner != owner) branch = new Branch(owner, branch);
    int index = (int) (hash >>> shift) & (WIDTH - 1);
    Node previous = branch.children[index];
    Node next = update(previous, position, hash, shift + BITS, column);
    branch.children[index] = next;
    if (previous == null && next != null) branch.count++;
    else if (previous != null && next == null) branch.count--;
    return branch.count == 0 ? null : branch;
  }
}

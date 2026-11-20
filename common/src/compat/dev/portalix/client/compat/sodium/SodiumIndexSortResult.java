package dev.portalix.client.compat.sodium;

public record SodiumIndexSortResult(byte[] indices, SodiumTopoSorterAccess.State topology) {}

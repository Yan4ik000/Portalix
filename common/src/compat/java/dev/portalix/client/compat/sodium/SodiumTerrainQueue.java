package dev.portalix.client.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.DeferredTaskList;
import org.joml.Vector3dc;

public record SodiumTerrainQueue(DeferredTaskList tasks, Vector3dc camera) {}

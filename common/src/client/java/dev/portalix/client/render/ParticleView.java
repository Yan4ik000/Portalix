package dev.portalix.client.render;

import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record ParticleView(Vec3 position, Vec3 right, Vec3 up, Predicate<AABB> frustum) {}

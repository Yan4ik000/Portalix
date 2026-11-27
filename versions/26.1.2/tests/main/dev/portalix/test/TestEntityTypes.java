package dev.portalix.test;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;

public final class TestEntityTypes {
  public static final EntityType<Arrow> ARROW = EntityType.ARROW;
  public static final EntityType<Blaze> BLAZE = EntityType.BLAZE;
  public static final EntityType<Chicken> CHICKEN = EntityType.CHICKEN;
  public static final EntityType<Husk> HUSK = EntityType.HUSK;
  public static final EntityType<Minecart> MINECART = EntityType.MINECART;
  public static final EntityType<Boat> OAK_BOAT = EntityType.OAK_BOAT;
  public static final EntityType<Pig> PIG = EntityType.PIG;
  public static final EntityType<Sheep> SHEEP = EntityType.SHEEP;
  public static final EntityType<Snowball> SNOWBALL = EntityType.SNOWBALL;
  public static final EntityType<ThrownTrident> TRIDENT = EntityType.TRIDENT;

  private TestEntityTypes() {}
}

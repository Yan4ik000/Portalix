package dev.portalix.test;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
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
  public static final EntityType<Arrow> ARROW = EntityTypes.ARROW;
  public static final EntityType<Blaze> BLAZE = EntityTypes.BLAZE;
  public static final EntityType<Chicken> CHICKEN = EntityTypes.CHICKEN;
  public static final EntityType<Husk> HUSK = EntityTypes.HUSK;
  public static final EntityType<Minecart> MINECART = EntityTypes.MINECART;
  public static final EntityType<Boat> OAK_BOAT = EntityTypes.OAK_BOAT;
  public static final EntityType<Pig> PIG = EntityTypes.PIG;
  public static final EntityType<Sheep> SHEEP = EntityTypes.SHEEP;
  public static final EntityType<Snowball> SNOWBALL = EntityTypes.SNOWBALL;
  public static final EntityType<ThrownTrident> TRIDENT = EntityTypes.TRIDENT;

  private TestEntityTypes() {}
}

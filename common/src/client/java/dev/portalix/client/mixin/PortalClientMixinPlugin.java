package dev.portalix.client.mixin;

import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.platform.ModPresence;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class PortalClientMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    if (mixinClassName.startsWith("dev.portalix.client.mixin.compat.iris.")) {
      return IrisShaders.available();
    }
    if (mixinClassName.startsWith("dev.portalix.client.mixin.terrain.vanilla.")) {
      return !ModPresence.INSTANCE.isLoaded("sodium");
    }
    if (mixinClassName.startsWith("dev.portalix.client.mixin.compat.sodium.")
        || mixinClassName.startsWith("dev.portalix.neoforge.mixin.compat.sodium.")) {
      return ModPresence.INSTANCE.isLoaded("sodium");
    }
    return true;
  }

  @Override
  public void onLoad(String mixinPackage) {}

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

  @Override
  public void postApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

package dev.portalix.client.mixin;

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
      return ModPresence.INSTANCE.isLoaded("iris");
    }
    if (mixinClassName.startsWith("dev.portalix.client.mixin.terrain.vanilla.")) {
      return !ModPresence.INSTANCE.isLoaded("sodium");
    }
    return !mixinClassName.startsWith("dev.portalix.client.mixin.compat.sodium.")
        || ModPresence.INSTANCE.isLoaded("sodium");
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

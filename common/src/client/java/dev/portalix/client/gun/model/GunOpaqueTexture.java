package dev.portalix.client.gun.model;

import dev.portalix.PortalixMod;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

final class GunOpaqueTexture extends SimpleTexture {
  // TextureManager owns these textures and reloads them from their original resources.
  private static final Map<Identifier, Identifier> TEXTURES = new HashMap<>();

  private GunOpaqueTexture(Identifier source) {
    super(source);
  }

  static Identifier of(Identifier source) {
    return TEXTURES.computeIfAbsent(source, texture -> {
      Identifier id = PortalixMod.id("opaque/" + texture.getNamespace() + "/" + texture.getPath());
      Minecraft.getInstance().getTextureManager().registerAndLoad(id, new GunOpaqueTexture(texture));
      return id;
    });
  }

  @Override
  public TextureContents loadContents(ResourceManager resources) throws IOException {
    try (var contents = super.loadContents(resources)) {
      // Alpha in an opaque Source material does not control surface coverage.
      return new TextureContents(
          contents.image().mappedCopy(color -> color | 0xFF000000), contents.metadata());
    }
  }
}

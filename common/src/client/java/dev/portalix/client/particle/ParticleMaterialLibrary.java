package dev.portalix.client.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.portalix.client.render.ParticleRenderTypes;
import dev.portalix.resource.ModAssetLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import net.minecraft.resources.Identifier;

final class ParticleMaterialLibrary {
  private final Map<String, Definition> systems;

  ParticleMaterialLibrary() {
    JsonObject sourceMaterials =
        ModAssetLoader.json("/assets/portalix/source_particles/materials.json");
    JsonObject sourceSystems =
        ModAssetLoader.json("/assets/portalix/source_particles/renderers.json");
    Map<String, Material> materials = new HashMap<>();

    for (Entry<String, JsonElement> entry : sourceMaterials.entrySet()) {
      try {
        JsonObject source = entry.getValue().getAsJsonObject();
        Map<Integer, Sheet> sheets = new HashMap<>();

        for (Entry<String, JsonElement> sheet : source.getAsJsonObject("sheet").entrySet()) {
          JsonObject value = sheet.getValue().getAsJsonObject();
          List<UV> frames = new ArrayList<>();

          for (JsonElement frame : value.getAsJsonArray("frames")) {
            JsonObject data = frame.getAsJsonObject();
            JsonArray uv = data.getAsJsonArray("uv");
            float duration = data.get("duration").getAsFloat();
            if (uv.size() != 4 || !Float.isFinite(duration) || duration <= 0.0F) {
              throw new IllegalArgumentException("Invalid sheet frame " + entry.getKey());
            }

            frames.add(
                new UV(
                    uv.get(0).getAsFloat(),
                    uv.get(1).getAsFloat(),
                    uv.get(2).getAsFloat(),
                    uv.get(3).getAsFloat(),
                    duration));
          }

          sheets.put(
              Integer.parseInt(sheet.getKey()),
              new Sheet(
                  value.get("clamp").getAsBoolean(), value.get("duration").getAsFloat(), frames));
        }

        materials.put(
            entry.getKey(),
            new Material(
                Identifier.parse(source.get("texture").getAsString()),
                source.get("overbright").getAsInt(),
                source.get("noz").getAsBoolean(),
                source.get("texture_height").getAsInt(),
                sheets));
      } catch (RuntimeException cause) {
        throw new IllegalArgumentException("Source material " + entry.getKey(), cause);
      }
    }

    Map<String, Definition> definitions = new HashMap<>();

    for (Entry<String, JsonElement> entry : sourceSystems.entrySet()) {
      try {
        JsonObject source = entry.getValue().getAsJsonObject();
        List<Renderer> renderers = new ArrayList<>();

        for (JsonElement item : source.getAsJsonArray("renderers")) {
          JsonObject value = item.getAsJsonObject();
          JsonObject attributes = value.getAsJsonObject("attributes");

          Kind kind =
              switch (value.get("name").getAsString()) {
                case "render_rope" -> Kind.ROPE;
                case "render_animated_sprites" -> Kind.SPRITES;
                default ->
                    throw new IllegalArgumentException("Unsupported Source renderer " + value);
              };
          int subdivisions =
              attributes.has("subdivision_count")
                  ? attributes.get("subdivision_count").getAsInt()
                  : 3;
          float texel =
              attributes.has("texel_size") ? attributes.get("texel_size").getAsFloat() : 4.0F;
          float scroll =
              attributes.has("texture_scroll_rate")
                  ? attributes.get("texture_scroll_rate").getAsFloat()
                  : 0.0F;
          if (subdivisions < 1
              || subdivisions > 64
              || !Float.isFinite(texel)
              || texel <= 0.0F
              || !Float.isFinite(scroll)) {
            throw new IllegalArgumentException("Invalid Source rope parameters " + entry.getKey());
          }

          renderers.add(new Renderer(kind, subdivisions, texel, scroll));
        }

        Material material =
            source.has("material") ? materials.get(source.get("material").getAsString()) : null;
        if (!renderers.isEmpty() && material == null) {
          throw new IllegalArgumentException("Missing Source material " + entry.getKey());
        }

        definitions.put(entry.getKey(), new Definition(material, renderers));
      } catch (RuntimeException cause) {
        throw new IllegalArgumentException("Source system " + entry.getKey(), cause);
      }
    }

    this.systems = Map.copyOf(definitions);
  }

  void prepareRenderTypes() {
    for (Material material :
        systems.values().stream().map(Definition::material).distinct().toList()) {
      if (material == null) continue;
      ParticleRenderTypes.source(material.texture(), material.overbright(), material.ignoreDepth());
      ParticleRenderTypes.spritecard(material.texture(), material.overbright(), material.ignoreDepth());
    }
  }

  Definition system(String name) {
    Definition value = this.systems.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Unknown Source system " + name);
    }
    return value;
  }

  Set<String> ropes() {
    Set<String> names = new HashSet<>();
    this.systems.forEach(
        (name, value) -> {
          if (value.renderers.stream().anyMatch(renderer -> renderer.kind == Kind.ROPE)) {
            names.add(name);
          }
        });
    return Set.copyOf(names);
  }

  record Definition(Material material, List<Renderer> renderers) {
    Definition {
      renderers = List.copyOf(renderers);
    }
  }

  enum Kind {
    ROPE,
    SPRITES;
  }

  record Material(
      Identifier texture, int overbright, boolean ignoreDepth, int height, Map<Integer, Sheet> sheets) {
    Material {
      sheets = Map.copyOf(sheets);
    }
  }

  record Renderer(Kind kind, int subdivisions, float texel, float scroll) {}

  record Sheet(boolean clamp, float duration, List<UV> frames) {
    Sheet {
      frames = List.copyOf(frames);
      if (frames.isEmpty() || !Float.isFinite(duration) || duration <= 0.0F) {
        throw new IllegalArgumentException("Invalid sprite sheet");
      }
    }

    UV sample(float age) {
      float at = this.clamp ? Math.max(0.0F, age) : Math.max(0.0F, age) % this.duration;

      for (UV frame : this.frames) {
        if (at < frame.duration) {
          return frame;
        }

        at -= frame.duration;
      }

      return this.frames.getLast();
    }
  }

  record UV(float u0, float v0, float u1, float v1, float duration) {
    static final UV FULL = new UV(0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
  }
}

package dev.portalix.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.portalix.client.config.PortalixSettingsScreen;

public final class PortalixModMenu implements ModMenuApi {
  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    return PortalixSettingsScreen::new;
  }
}

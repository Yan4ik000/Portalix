package dev.portalix.test.neoforge;

import dev.portalix.test.client.ClientRegressionRunner;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "portalix_regression", dist = Dist.CLIENT)
public final class RegressionClient {
  public RegressionClient() {
    NeoForge.EVENT_BUS.addListener((CalculateDetachedCameraDistanceEvent event) ->
        event.setDistance(ClientRegressionRunner.detachedCameraDistance(event.getDistance())));
  }
}

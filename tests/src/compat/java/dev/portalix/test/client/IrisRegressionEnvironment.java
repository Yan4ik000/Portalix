package dev.portalix.test.client;

import java.util.LinkedHashMap;

public final class IrisRegressionEnvironment implements ClientRegressionRunner.ShaderEnvironment {
  @Override
  public void reloadShadersAfterResourceReload() {
    try {
      net.irisshaders.iris.Iris.reload();
    } catch (java.io.IOException failure) {
      throw new java.io.UncheckedIOException("Cannot reload the active shader pack", failure);
    }
  }

  @Override
  public ClientRegressionRunner.ShaderConfiguration verify() {
    var settings = new LinkedHashMap<String, String>();
    ClientAssertions.check(net.irisshaders.iris.Iris.getIrisConfig().areShadersEnabled(),
        "Iris shader configuration is disabled");
    String shader = net.irisshaders.iris.Iris.getCurrentPackName();
    ClientAssertions.check("ComplementaryUnbound_r5.9.3.zip".equals(shader),
        "Complementary Unbound must be active; actual pack " + shader);
    var pack = net.irisshaders.iris.Iris.getCurrentPack().orElseThrow(() -> new AssertionError("Active shader pack missing"));
    String shaderProfile = pack.getProfileInfo();
    ClientAssertions.check(shaderProfile.equals("Profile: HIGH (+0 options changed by user)"),
        "The shader pack must use its unmodified default settings: " + shaderProfile);
    var shaderOptions = pack.getShaderPackOptions();
    var values = shaderOptions.getOptionValues();
    shaderOptions.getOptionSet().getBooleanOptions().keySet().stream().sorted().forEach(
        name -> settings.put(name, Boolean.toString(values.getBooleanValueOrDefault(name))));
    shaderOptions.getOptionSet().getStringOptions().keySet().stream().sorted().forEach(
        name -> settings.put(name, values.getStringValueOrDefault(name)));
    return new ClientRegressionRunner.ShaderConfiguration(shader, shaderProfile, settings);
  }
}

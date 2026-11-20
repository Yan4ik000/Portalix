package dev.portalix.client.compat.iris;

import net.irisshaders.iris.Iris;

public interface IrisPipelineHistory extends IrisViewHistory {
  Object portalix$uniformHistoryToken();

  static Object currentToken() {
    if (IrisScene.preparing()) return null;
    var pipeline = Iris.getPipelineManager().getPipelineNullable();
    return pipeline instanceof IrisPipelineHistory history ? history.portalix$uniformHistoryToken() : null;
  }
}

package dev.portalix.client.gun.model;

public enum GunAttachment {
  MUZZLE("muzzle"),
  ARM_1("Arm1_attach3"),
  ARM_2("Arm2_attach3"),
  ARM_3("Arm3_attach3"),
  BORE_FRONT("Bore_front"),
  BORE_REAR("Bore_rear"),
  BEAM_POINT_1("Beam_point1"),
  BODY_LIGHT("Body_light");
  private final String key;

  GunAttachment(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}

package dev.portalix.client.compat.asyncparticles;

import dev.portalix.platform.ModPresence;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public final class AsyncParticlesCompatibility {
  private static final String MOD_ID = "asyncparticles";
  private static final String CLIENT_PACKAGE = "fun/qu_an/minecraft/asyncparticles/client/";
  private static final String GPU_PACKAGE = CLIENT_PACKAGE + "core/particle/gpu_acceleration/";
  private static final String COMPUTE_RESULT = GPU_PACKAGE + "ComputeResult";

  public static final String BEGIN_FRAME = "beginFrame(F)V";
  public static final String COMPUTE = "compute()V";
  public static final String ENSURE_COMPUTE_READY = "ensureComputeReady()L" + COMPUTE_RESULT + ";";

  private static final int NO_INSTANCE_BODY = Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE;
  private static final boolean SUPPORTED = ModPresence.INSTANCE.isLoaded(MOD_ID) && hasGpuApi();

  private AsyncParticlesCompatibility() {}

  public static boolean isSupported() {
    return SUPPORTED;
  }

  private static boolean hasGpuApi() {
    try {
      var behavior = readClass(GPU_PACKAGE + "GpuParticleBehavior");
      if ((behavior.access & Opcodes.ACC_INTERFACE) != 0
          || !hasMethod(behavior, BEGIN_FRAME, 0, NO_INSTANCE_BODY)
          || !hasMethod(behavior, COMPUTE, 0, NO_INSTANCE_BODY)
          || !hasMethod(behavior, ENSURE_COMPUTE_READY, 0, NO_INSTANCE_BODY)) return false;
      readClass(COMPUTE_RESULT);

      var config = readClass(CLIENT_PACKAGE + "config/ConfigHelper");
      if ((config.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE)) != Opcodes.ACC_PUBLIC
          || !hasMethod(config, "isGpuParticles()Z", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, 0)) return false;

      var group = readClass(CLIENT_PACKAGE + "addon/GpuParticleGroup");
      int publicInterface = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE;
      return (group.access & publicInterface) == publicInterface
          && group.methods.stream().anyMatch(method ->
              method.name.equals("asyncparticles$getGpuParticles")
                  && method.desc.equals("()Ljava/util/Queue;")
                  && "()Ljava/util/Queue<Lnet/minecraft/client/particle/SingleQuadParticle;>;".equals(method.signature)
                  && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC);
    } catch (IOException | IllegalArgumentException unavailable) {
      return false;
    }
  }

  private static ClassNode readClass(String name) throws IOException {
    String path = name + ".class";
    try (var bytes = ModPresence.INSTANCE.openResource(MOD_ID, path)) {
      if (bytes == null) throw new FileNotFoundException(path);
      var type = new ClassNode();
      new ClassReader(bytes).accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
      return type;
    }
  }

  private static boolean hasMethod(ClassNode owner, String selector, int required, int excluded) {
    return owner.methods.stream().anyMatch(method ->
        selector.equals(method.name + method.desc) && (method.access & (required | excluded)) == required);
  }
}

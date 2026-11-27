import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.gradle.wrapper.GradleWrapperMain;

class PortalixGradle {
  private static final String PROJECT_CACHE_OPTION = "project-cache-dir";
  private static final String PROJECT_CACHE_PROPERTY = "org.gradle.projectcachedir";
  private static final String GRADLE_USER_HOME_PROPERTY = "gradle.user.home";

  public static void main(String[] args) throws Exception {
    Path wrapper = Path.of(GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path root = wrapper.getParent().getParent().getParent().toRealPath();
    GradleWrapperMain.main(withProjectCache(args, root));
  }

  private static String[] withProjectCache(String[] args, Path root) throws IOException {
    String projectDir = ".";
    String userHome = null;
    Map<String, String> projectArgs = new HashMap<>();
    Map<String, String> systemArgs = new HashMap<>();
    for (int index = 0; index < args.length; index++) {
      String arg = args[index];
      if (arg.equals("--")) break;
      if (!arg.startsWith("-") || arg.length() < 2) continue;
      String option = arg.startsWith("--") ? arg.split("=", 2)[0] : arg.substring(0, 2);
      if (option.equals("--" + PROJECT_CACHE_OPTION)) return args;
      switch (option) {
        case "-p", "--project-dir", "-g", "--gradle-user-home", "-P", "--project-prop", "-D", "--system-prop" -> {
          if (arg.equals(option) && index == args.length - 1) return args;
          String value = arg.equals(option) ? args[++index] : arg.substring(option.length());
          if (!arg.equals(option) && value.startsWith("=")) value = value.substring(1);
          switch (option) {
            case "-p", "--project-dir" -> projectDir = value;
            case "-g", "--gradle-user-home" -> userHome = value;
            case "-P", "--project-prop" -> putProperty(projectArgs, value);
            default -> putProperty(systemArgs, value);
          }
        }
        default -> { }
      }
    }
    if (!belongsToBuild(Path.of(projectDir), root)) return args;

    var projectProperties = properties(root.resolve("gradle.properties"));
    if (userHome == null) {
      userHome = systemArgs.getOrDefault(GRADLE_USER_HOME_PROPERTY,
          projectProperties.getProperty("systemProp." + GRADLE_USER_HOME_PROPERTY,
              System.getProperty(GRADLE_USER_HOME_PROPERTY, System.getenv("GRADLE_USER_HOME"))));
    }
    Path userDirectory = userHome == null ? Path.of(System.getProperty("user.home"), ".gradle") : Path.of(userHome);
    var userProperties = properties(userDirectory.resolve("gradle.properties"));
    if (systemArgs.containsKey(PROJECT_CACHE_PROPERTY) || System.getProperty(PROJECT_CACHE_PROPERTY) != null
        || userProperties.containsKey(PROJECT_CACHE_PROPERTY) || projectProperties.containsKey(PROJECT_CACHE_PROPERTY)) {
      return args;
    }

    String version = projectProperty("mcVersion", projectArgs, systemArgs, userProperties, projectProperties);
    if (version == null) {
      version = projectProperty("default_minecraft_version", projectArgs, systemArgs, userProperties, projectProperties);
    }
    if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._+-]*")) return args;

    // Gradle opens its execution history before any init or settings script runs.
    Path cache = root.resolve(".gradle/minecraft").resolve(version);
    String[] configured = new String[args.length + 1];
    configured[0] = "--" + PROJECT_CACHE_OPTION + "=" + cache;
    System.arraycopy(args, 0, configured, 1, args.length);
    return configured;
  }

  private static boolean belongsToBuild(Path project, Path root) throws IOException {
    if (!Files.isDirectory(project)) return false;
    for (Path dir = project.toRealPath(); dir.startsWith(root); dir = dir.getParent()) {
      if (dir.equals(root)) return true;
      if (Files.isRegularFile(dir.resolve("settings.gradle")) || Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
        return false;
      }
    }
    return false;
  }

  private static Properties properties(Path file) throws IOException {
    var result = new Properties();
    if (Files.isRegularFile(file)) {
      try (var input = Files.newInputStream(file)) {
        result.load(input);
      }
    }
    return result;
  }

  private static void putProperty(Map<String, String> properties, String value) {
    String[] pair = value.split("=", 2);
    properties.put(pair[0], pair.length == 2 ? pair[1] : "");
  }

  private static String projectProperty(String name, Map<String, String> projectArgs, Map<String, String> systemArgs,
      Properties userProperties, Properties projectProperties) {
    if (projectArgs.containsKey(name)) return projectArgs.get(name);
    String systemName = "org.gradle.project." + name;
    String systemValue = systemArgs.getOrDefault(systemName, userProperties.getProperty("systemProp." + systemName,
        projectProperties.getProperty("systemProp." + systemName, System.getProperty(systemName))));
    if (systemValue != null) return systemValue;
    String environmentValue = System.getenv("ORG_GRADLE_PROJECT_" + name);
    if (environmentValue != null) return environmentValue;
    return userProperties.getProperty(name, projectProperties.getProperty(name));
  }
}

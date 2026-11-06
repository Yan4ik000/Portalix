import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

abstract class PrepareRegressionRun extends DefaultTask {
    @Input abstract Property<String> getProfileName()
    @Input abstract Property<String> getClientTestSelection()
    @Input abstract Property<String> getLoaderName()
    @Input abstract Property<String> getModuleName()
    @Input abstract Property<String> getBackend()
    @Input abstract Property<Boolean> getShaders()
    @Input abstract Property<Boolean> getSupported()
    @Internal abstract DirectoryProperty getRunDirectory()
    @Internal abstract DirectoryProperty getReportDirectory()
    @Internal abstract DirectoryProperty getShaderCacheDirectory()
    @InputFiles @PathSensitive(PathSensitivity.NONE) abstract ConfigurableFileCollection getMinecraftClasspath()

    static final String SHADER = 'ComplementaryUnbound_r5.9.3.zip'
    static final String SHADER_URL = 'https://cdn.modrinth.com/data/R6NEzAwj/versions/B1kyfoUZ/' + SHADER
    static final String SHADER_HASH = 'a085d1d8cda67cd17a2b414eeba48bbd809da069f269bab46246235ec8fe714a275013c70d0504527b0987d810deddddba4564332414ab3b3bd7435932ff808c'
    static final String IRIS_VULKAN_REASON = 'The reviewed Iris version disables shader support on the native Vulkan backend. OpenGL substitution is forbidden.'

    @TaskAction void prepare() {
        if (!profileMatchesModule(profileName.get(), loaderName.get(), moduleName.get()))
            throw new GradleException("Profile ${profileName.get()} belongs to ${loaderName.get()}, not ${moduleName.get()}; use :testClient or the matching loader task")
        File report = reportDirectory.get().asFile
        report.mkdirs()
        // A failed launch must never inherit a passing report from an earlier run.
        ['results.json', 'TEST-regression.xml', 'launch-start.json', 'launch-diagnostics.json', 'game.log'].each {
            java.nio.file.Files.deleteIfExists(new File(report, it).toPath())
        }
        report.listFiles()?.findAll { it.name ==~ /portal-view-\d+\.png/ }?.each {
            java.nio.file.Files.deleteIfExists(it.toPath())
        }
        def evidence = new File(report, 'evidence').toPath().toAbsolutePath().normalize()
        if (!evidence.startsWith(report.toPath().toAbsolutePath().normalize()))
            throw new GradleException("Evidence directory escaped its report directory: ${evidence}")
        if (java.nio.file.Files.exists(evidence, java.nio.file.LinkOption.NOFOLLOW_LINKS)) PrepareRegressionRun.deleteTree(evidence)
        if (!supported.get()) {
            if (!irisVulkan(profileName.get(), backend.get(), shaders.get()))
                throw new GradleException('Unsupported profile does not match the known Iris Vulkan limitation')
            String reason = IRIS_VULKAN_REASON
            new File(report, 'results.json').setText(JsonOutput.prettyPrint(JsonOutput.toJson(
                [profile: profileName.get(), backend: backend.get(), shaders: shaders.get(),
                    phase: 'preflight', status: 'unsupported', reason: reason])), 'UTF-8')
            new File(report, 'TEST-regression.xml').setText(
                '<testsuite name="compatibility" tests="1" skipped="1"><testcase name="iris-vulkan"><skipped message="Iris cannot enable shaders on Vulkan"/></testcase></testsuite>', 'UTF-8')
            throw new GradleException(reason)
        }
        File run = runDirectory.get().asFile
        run.mkdirs()
        if (profileName.get().startsWith('neoforge-')) {
            File loaderConfig = new File(run, 'config/fml.toml')
            loaderConfig.parentFile.mkdirs()
            String configuration = loaderConfig.isFile() ? loaderConfig.getText('UTF-8') : ''
            if (configuration =~ /(?m)^\s*earlyWindowControl\s*=/) {
                configuration = configuration.replaceAll(/(?m)^\s*earlyWindowControl\s*=.*$/, 'earlyWindowControl=false')
            } else {
                configuration += '\nearlyWindowControl=false\n'
            }
            loaderConfig.setText(configuration, 'UTF-8')
        }
        File saves = new File(run, 'saves')
        saves.listFiles()?.findAll { it.name.startsWith('portalix-regression-') }?.each { world ->
            def target = world.toPath().toAbsolutePath().normalize()
            if (!target.startsWith(saves.toPath().toAbsolutePath().normalize()))
                throw new GradleException("Test world escaped its run directory: ${target}")
            PrepareRegressionRun.deleteTree(target)
        }
        Integer dataVersion = null
        for (File artifact : minecraftClasspath.files) {
            if (!artifact.name.endsWith('.jar')) continue
            new java.util.zip.ZipFile(artifact).withCloseable { zip ->
                def entry = zip.getEntry('version.json')
                if (entry != null) zip.getInputStream(entry).withCloseable {
                    def version = new JsonSlurper().parse(it, 'UTF-8')
                    if (version.world_version instanceof Number) dataVersion = version.world_version as int
                }
            }
            if (dataVersion != null) break
        }
        if (dataVersion == null) throw new GradleException('Cannot determine Minecraft options data version')
        new File(run, 'options.txt').setText("version:${dataVersion}\n" + '''enableVsync:false
maxFps:260
fullscreen:false
overrideWidth:1856
overrideHeight:1044
pauseOnLostFocus:false
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
autoJump:false
renderDistance:16
simulationDistance:16
entityDistanceScaling:5.0
particles:0
soundCategory_master:0.2
soundCategory_music:0.0
biomeBlendRadius:7
mipmapLevels:4
guiScale:2
''' + "preferredGraphicsBackend:\"${backend.get()}\"\n", 'UTF-8')
        if (shaders.get()) {
            File cached = new File(shaderCacheDirectory.get().asFile, SHADER)
            if (!cached.exists()) {
                cached.parentFile.mkdirs()
                File temporary = new File(cached.parentFile, SHADER + '.part')
                def connection = URI.create(SHADER_URL).toURL().openConnection()
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.setRequestProperty('User-Agent', 'Portalix-regression-tests/1')
                connection.inputStream.withCloseable { input -> temporary.withOutputStream { it << input } }
                verifyShader(temporary)
                java.nio.file.Files.move(temporary.toPath(), cached.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            verifyShader(cached)
            File shader = new File(run, 'shaderpacks/' + SHADER)
            shader.parentFile.mkdirs()
            java.nio.file.Files.copy(cached.toPath(), shader.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            File config = new File(run, 'config/iris.properties')
            config.parentFile.mkdirs()
            config.setText("enableShaders=true\nshaderPack=${SHADER}\ndisableUpdateMessage=true\n", 'UTF-8')
            java.nio.file.Files.deleteIfExists(new File(run, 'shaderpacks/' + SHADER + '.txt').toPath())
        }
        new File(report, 'launch-start.json').setText(JsonOutput.toJson(
            [profile: profileName.get(), clientTest: clientTestSelection.get(), startedAt: System.currentTimeMillis()]), 'UTF-8')
    }

    static boolean irisVulkan(String profile, String backend, boolean shaders) {
        profile in ['fabric-sodium-iris-vulkan', 'neoforge-sodium-iris-vulkan'] && backend == 'vulkan' && shaders
    }

    static boolean profileMatchesModule(String profile, String loader, String module) {
        loader in ['fabric', 'neoforge'] && module == loader && profile.startsWith(loader + '-')
    }

    private static void deleteTree(java.nio.file.Path path) {
        java.nio.file.Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attributes) {
                java.nio.file.Files.delete(file)
                return java.nio.file.FileVisitResult.CONTINUE
            }
            @Override java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path directory, IOException failure) {
                if (failure != null) throw failure
                java.nio.file.Files.delete(directory)
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
    }

    private static void verifyShader(File file) {
        def digest = MessageDigest.getInstance('SHA-512')
        file.withInputStream { input ->
            byte[] buffer = new byte[65536]
            int count
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count)
        }
        if (digest.digest().encodeHex().toString() != SHADER_HASH)
            throw new GradleException("Complementary Unbound checksum mismatch: ${file}")
    }
}

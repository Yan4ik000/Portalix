import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

abstract class VerifyModArchive extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) abstract RegularFileProperty getArchiveFile()
    @Input abstract Property<String> getLoaderName()
    @Input abstract Property<String> getModVersion()
    @Input abstract Property<Boolean> getRenderCompat()
    @Input abstract MapProperty<String, String> getDeclaredRenderVersions()
    @Input abstract ListProperty<String> getRequiredResources()
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) abstract ConfigurableFileCollection getClassDirectories()

    @TaskAction void verify() {
        Set<String> classes = [] as Set
        classDirectories.files.each { root ->
            if (!root.isDirectory()) return
            root.eachFileRecurse { file ->
                if (file.isFile() && file.name.endsWith('.class'))
                    classes.add(root.toPath().relativize(file.toPath()).toString().replace('\\', '/'))
            }
        }
        verify(archiveFile.get().asFile, loaderName.get(), modVersion.get(), renderCompat.get(),
            requiredResources.get() as Set, classes, declaredRenderVersions.get())
    }

    static void verify(File file, String loader, String version, boolean compat,
            Set<String> resources, Set<String> classes, Map<String, String> renderVersions) {
        new ZipFile(file).withCloseable { archive ->
            List<String> names = archive.entries().toList().findAll { !it.directory }.collect { it.name }
            require(names.size() == names.toSet().size(), 'Duplicate archive entries')
            Set<String> entries = names as Set
            require(classes && classes == (entries.findAll { it.endsWith('.class') } as Set),
                'Packaged classes differ from the compiled main and client outputs')
            Set<String> required = resources + ['LICENSE', 'NOTICE']
            require(entries.containsAll(required), "Missing resources: ${required - entries}")
            require(!entries.any { it.startsWith('dev/portalix/test/') || it.startsWith('portalix-regression')
                || it.startsWith('data/portalix_regression/') || it.contains('/.agents/') },
                'Test or dependency-source material leaked into the release archive')
            require(ModArchiveMetadata.versions(archive, loader)['portalix'] == version,
                'Packaged mod identity/version differs from this build')
            String metadata
            List<String> configs
            List<String> entrypoints = []
            if (loader == 'fabric') {
                require(!readText(archive, 'fabric.mod.json').contains('${'), 'Unexpanded Fabric metadata')
                def json = readJson(archive, 'fabric.mod.json')
                require(json.accessWidener == 'portalix.accesswidener' && entries.contains(json.accessWidener),
                    'Fabric access widener is absent or misdeclared')
                configs = json.mixins.collect { it instanceof Map ? it.config : it }
                entrypoints.addAll(json.entrypoints.values().flatten())
                renderVersions.each { id, selected ->
                    require(compat ? json.breaks?.get(id) == ["<${selected}".toString(), ">${selected}".toString()] :
                        json.breaks?.get(id) == '*', "Packaged Fabric ${id} constraints do not match this product")
                }
            } else {
                metadata = readText(archive, 'META-INF/neoforge.mods.toml')
                require(!metadata.contains('${'), 'Unexpanded NeoForge metadata')
                configs = (metadata =~ /(?m)^\s*config\s*=\s*"([^"]+)"/).collect { it[1] }
                entrypoints.addAll(['dev.portalix.neoforge.PortalixNeoForge',
                    'dev.portalix.neoforge.client.PortalixNeoForgeClient'])
                require(entries.contains('META-INF/accesstransformer.cfg') && !entries.contains('portalix.accesswidener'),
                    'NeoForge access rules are absent or include the Fabric access widener')
                renderVersions.each { id, selected ->
                    def sections = metadata.split(/(?m)^\[\[dependencies\.portalix]]/).drop(1)
                    def dependency = sections.findAll { (it =~ /(?m)^\s*modId\s*=\s*"${id}"/).find() }
                    require(dependency.size() == 1, "Missing or duplicate NeoForge dependency ${id}")
                    require(ModArchiveMetadata.stringValue(dependency[0], 'type') == (compat ? 'optional' : 'incompatible') &&
                        ModArchiveMetadata.stringValue(dependency[0], 'versionRange') == (compat ? "[${selected}]" : '[0,)'),
                        "Packaged NeoForge ${id} constraints do not match this product")
                }
            }
            require(configs.contains('portalix.compat.mixins.json') == compat,
                'Mixin metadata does not match the selected render compatibility product')
            require(entries.contains('portalix.compat.mixins.json') == compat,
                'Compat mixin resources do not match the selected product')
            configs.each { path ->
                def config = readJson(archive, path)
                ['mixins', 'client', 'server'].each { side ->
                    (config[side] ?: []).each { name ->
                        require(entries.contains("${config.package.replace('.', '/')}/${name.replace('.', '/')}.class".toString()),
                            "Missing registered mixin ${config.package}.${name}")
                    }
                }
                if (config.plugin) entrypoints.add(config.plugin)
            }
            entries.findAll { it.startsWith('META-INF/services/') }.each { service ->
                readText(archive, service).readLines().collect { it.split('#', 2)[0].trim() }
                    .findAll { it }.each { provider -> entrypoints.add(provider) }
            }
            ['dev.portalix.platform.ModPresence', 'dev.portalix.platform.PortalPlatform',
             'dev.portalix.platform.RegistryPlatform', 'dev.portalix.client.platform.ClientPlatform'].each { service ->
                String path = "META-INF/services/${service}"
                require(entries.contains(path), "Missing platform service ${service}")
                def providers = readText(archive, path).readLines().collect { it.split('#', 2)[0].trim() }.findAll { it }
                require(providers.size() == 1 && providers[0].contains(".${loader}."),
                    "Missing, ambiguous or wrong-loader platform service ${service}")
            }
            entrypoints.each { name ->
                require(entries.contains(name.replace('.', '/') + '.class'), "Missing entrypoint/provider ${name}")
            }
        }
    }

    private static Map readJson(ZipFile archive, String path) {
        new JsonSlurper().parseText(readText(archive, path)) as Map
    }

    private static String readText(ZipFile archive, String path) {
        def entry = archive.getEntry(path)
        require(entry != null, "Missing archive resource ${path}")
        archive.getInputStream(entry).withCloseable { it.getText('UTF-8') }
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new GradleException(message)
    }
}

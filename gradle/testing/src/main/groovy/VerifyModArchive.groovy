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
    @Input abstract Property<String> getMinecraftVersion()
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
        verify(archiveFile.get().asFile, loaderName.get(), modVersion.get(), minecraftVersion.get(),
            requiredResources.get() as Set, classes, declaredRenderVersions.get())
    }

    static void verify(File file, String loader, String version, String minecraft,
            Set<String> resources, Set<String> classes, Map<String, String> renderVersions) {
        new ZipFile(file).withCloseable { archive ->
            List<String> names = archive.entries().toList().findAll { !it.directory }.collect { it.name }
            require(names.size() == names.toSet().size(), 'Duplicate archive entries')
            Set<String> entries = names as Set
            require(classes && classes == (entries.findAll { it.endsWith('.class') } as Set),
                'Packaged classes differ from the compiled main and client outputs')
            Set<String> required = resources + ['LICENSE.md', 'META-INF/MANIFEST.MF']
            require(entries.containsAll(required), "Missing resources: ${required - entries}")
            Set<String> unexpected = (entries - classes - required).findAll {
                !(it ==~ /META-INF\/[A-Z0-9_-]{1,8}\.(SF|RSA|DSA|EC)/)
            }
            require(unexpected.isEmpty(), "Unexpected archive resources: ${unexpected}")
            require(!entries.any { it.startsWith('dev/portalix/test/') || it.startsWith('portalix-regression')
                || it.startsWith('data/portalix_regression/') || it.contains('/.agents/') },
                'Test or dependency-source material leaked into the release archive')
            require(ModArchiveMetadata.versions(archive, loader)['portalix'] == version,
                'Packaged mod identity/version differs from this build')
            List<String> configs
            List<String> entrypoints = []
            if (loader == 'fabric') {
                require(!readText(archive, 'fabric.mod.json').contains('${'), 'Unexpanded Fabric metadata')
                def json = readJson(archive, 'fabric.mod.json')
                require(json.depends?.minecraft == minecraft,
                    "Packaged Fabric Minecraft dependency must be ${minecraft}")
                require(json.accessWidener == 'portalix.accesswidener' && entries.contains(json.accessWidener),
                    'Fabric access widener is absent or misdeclared')
                Map<String, String> expectedConfigs = [
                    'portalix.mixins.json': '*',
                    'portalix.fabric.mixins.json': '*',
                    'portalix.client.mixins.json': 'client',
                    'portalix.fabric.client.mixins.json': 'client',
                    'portalix.compat.mixins.json': 'client'
                ]
                require(json.mixins instanceof List, 'Missing Fabric mixin registrations')
                Map<String, String> registrations = [:]
                json.mixins.each { registration ->
                    String config = registration instanceof Map ? registration.config : registration
                    String environment = registration instanceof Map ? registration.get('environment', '*') : '*'
                    require(!registrations.containsKey(config), "Duplicate Fabric mixin registration ${config}")
                    registrations[config] = environment
                }
                require(registrations == expectedConfigs,
                    "Expected Fabric mixin registrations ${expectedConfigs}, found ${registrations}")
                configs = registrations.keySet().toList()
                entrypoints.addAll(json.entrypoints.values().flatten())
                renderVersions.each { id, selected ->
                    require(json.breaks?.get(id) == ["<${selected}".toString(), ">${selected}".toString()],
                        "Packaged Fabric ${id} constraints must allow only ${selected}")
                }
            } else {
                String metadata = readText(archive, 'META-INF/neoforge.mods.toml')
                require(!metadata.contains('${'), 'Unexpanded NeoForge metadata')
                configs = (metadata =~ /(?ms)^\s*\[\[mixins]]\s*\n(.*?)(?=^\s*\[|\z)/).collect {
                    ModArchiveMetadata.stringValue(it[1], 'config')
                }
                Set<String> expectedConfigs = ['portalix.mixins.json', 'portalix.neoforge.mixins.json',
                    'portalix.client.mixins.json', 'portalix.compat.mixins.json'] as Set
                require(configs.size() == expectedConfigs.size() && configs.toSet() == expectedConfigs,
                    "Expected NeoForge mixin registrations ${expectedConfigs}, found ${configs}")
                entrypoints.addAll(['dev.portalix.neoforge.PortalixNeoForge',
                    'dev.portalix.neoforge.client.PortalixNeoForgeClient'])
                require(entries.contains('META-INF/accesstransformer.cfg') && !entries.contains('portalix.accesswidener'),
                    'NeoForge access rules are absent or include the Fabric access widener')
                def sections = metadata.split(/(?m)^\[\[dependencies\.portalix]]/).drop(1)
                ([minecraft: minecraft] + renderVersions).each { id, selected ->
                    def dependency = sections.findAll { (it =~ /(?m)^\s*modId\s*=\s*"${id}"/).find() }
                    require(dependency.size() == 1, "Missing or duplicate NeoForge dependency ${id}")
                    String type = id == 'minecraft' ? 'required' : 'optional'
                    require(ModArchiveMetadata.stringValue(dependency[0], 'type') == type &&
                        ModArchiveMetadata.stringValue(dependency[0], 'versionRange') == "[${selected}]",
                        "Packaged NeoForge ${id} must be ${type} and allow only ${selected}")
                }
            }
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
            String serviceDirectory = 'META-INF/services/'
            Map<String, List<String>> services = [:]
            entries.findAll { it.startsWith(serviceDirectory) }.each { path ->
                def providers = readText(archive, path).readLines().collect { it.split('#', 2)[0].trim() }.findAll { it }
                services[path.substring(serviceDirectory.length())] = providers
                entrypoints.addAll(providers)
            }
            ['dev.portalix.platform.ModPresence', 'dev.portalix.platform.PortalPlatform',
             'dev.portalix.platform.RegistryPlatform', 'dev.portalix.client.platform.ClientPlatform'].each { service ->
                def providers = services[service]
                require(providers != null, "Missing platform service ${service}")
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

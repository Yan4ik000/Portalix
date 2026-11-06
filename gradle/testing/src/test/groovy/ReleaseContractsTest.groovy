import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import static org.junit.jupiter.api.Assertions.assertThrows

class ReleaseContractsTest {
    @TempDir Path directory

    @Test void resolvedArtifactsMustAgreeWithExactPublishedCompatibilityVersions() {
        ['fabric', 'neoforge'].each { loader ->
            def sodium = archive("${loader}-sodium.jar", metadata(loader, 'sodium', '0.9.2+mc26.2'))
            def iris = archive("${loader}-iris.jar", metadata(loader, 'iris', '1.11.4+mc26.2'))
            def expected = [sodium: '0.9.2+mc26.2', iris: '1.11.4+mc26.2']
            VerifyRenderDependencies.verify([sodium, iris], loader, expected)
            [[sodium], [sodium, iris, iris]].each { files ->
                assertThrows(GradleException) { VerifyRenderDependencies.verify(files, loader, expected) }
            }
            assertThrows(GradleException) {
                VerifyRenderDependencies.verify([sodium, iris], loader, expected + [iris: '1.12.0+mc26.2'])
            }
        }
    }

    @Test void archiveVerificationUsesThePackagedProductAndRejectsOmissionsAndStaleMetadata() {
        ['fabric', 'neoforge'].each { loader ->
            [true, false].each { compat ->
                Map<String, String> entries = product(loader, compat)
                Set<String> classes = entries.keySet().findAll { it.endsWith('.class') } as Set
                def versions = [sodium: '0.9.2+mc26.2', iris: '1.11.4+mc26.2']
                def verify = { Map<String, String> contents ->
                    VerifyModArchive.verify(archive("${loader}-${compat}.jar", contents), loader, '0.1.0', compat,
                        ['assets/portalix/required.bin'] as Set, classes, versions)
                }
                verify(entries)
                if (compat) {
                    String path = loader == 'fabric' ? 'fabric.mod.json' : 'META-INF/neoforge.mods.toml'
                    String relaxed = loader == 'fabric' ? entries[path].replace('>0.9.2+mc26.2', '*') :
                        entries[path].replace('[0.9.2+mc26.2]', '[0,)')
                    assertThrows(GradleException) { verify(entries + [(path): relaxed]) }
                }
                ['assets/portalix/required.bin', 'dev/portalix/PortalixMod.class',
                 'META-INF/services/dev.portalix.platform.PortalPlatform'].each { missing ->
                    assertThrows(GradleException) { verify(entries.findAll { key, value -> key != missing }) }
                }
                assertThrows(GradleException) { verify(entries + ['dev/portalix/test/Leaked.class': 'class']) }
                assertThrows(GradleException) { verify(entries + ['dev/portalix/Stale.class': 'class']) }
                String service = 'META-INF/services/dev.portalix.platform.PortalPlatform'
                assertThrows(GradleException) { verify(entries + [(service): 'dev.portalix.wrong.Platform']) }
                Map wrongVersion = metadata(loader, 'portalix', '0.2.0')
                assertThrows(GradleException) { verify(entries + wrongVersion) }
                assertThrows(GradleException) {
                    VerifyModArchive.verify(archive("opposite-${loader}-${compat}.jar", entries), loader, '0.1.0', !compat,
                        [] as Set, classes, versions)
                }
            }
        }
    }

    private static Map<String, String> metadata(String loader, String id, String version) {
        loader == 'fabric' ? ['fabric.mod.json': JsonOutput.toJson([id: id, version: version])] :
            ['META-INF/neoforge.mods.toml': "[[mods]]\nmodId=\"${id}\"\nversion=\"${version}\"\n"]
    }

    private static Map<String, String> product(String loader, boolean compat) {
        List<String> configs = ['portalix.mixins.json', 'portalix.client.mixins.json']
        if (compat) configs.add('portalix.compat.mixins.json')
        Map<String, String> entries = ['LICENSE': 'license', 'NOTICE': 'notice',
            'assets/portalix/required.bin': 'asset', 'dev/portalix/PortalixMod.class': 'class']
        configs.each { path -> entries[path] = JsonOutput.toJson([package: 'dev.portalix.mixin', mixins: ['Example']]) }
        entries['dev/portalix/mixin/Example.class'] = 'class'
        ['dev.portalix.platform.ModPresence', 'dev.portalix.platform.PortalPlatform',
         'dev.portalix.platform.RegistryPlatform', 'dev.portalix.client.platform.ClientPlatform'].eachWithIndex { service, i ->
            String provider = "dev.portalix.${loader}.Platform${i}"
            entries["META-INF/services/${service}".toString()] = provider
            entries[provider.replace('.', '/') + '.class'] = 'class'
        }
        if (loader == 'fabric') {
            entries['portalix.accesswidener'] = 'accessWidener v2 official'
            entries['fabric.mod.json'] = JsonOutput.toJson([id: 'portalix', version: '0.1.0',
                accessWidener: 'portalix.accesswidener', mixins: configs,
                entrypoints: [main: ['dev.portalix.PortalixMod']], breaks: compat ?
                    [sodium: ['<0.9.2+mc26.2', '>0.9.2+mc26.2'], iris: ['<1.11.4+mc26.2', '>1.11.4+mc26.2']] :
                    [sodium: '*', iris: '*']])
        } else {
            entries.putAll(metadata(loader, 'portalix', '0.1.0'))
            entries['META-INF/accesstransformer.cfg'] = 'public example.Type'
            String path = 'META-INF/neoforge.mods.toml'
            configs.each { config -> entries[path] += "\n[[mixins]]\nconfig=\"${config}\"\n" }
            ['sodium', 'iris'].each { id ->
                String range = compat ? "[${id == 'sodium' ? '0.9.2+mc26.2' : '1.11.4+mc26.2'}]" : '[0,)'
                entries[path] += "\n[[dependencies.portalix]]\nmodId=\"${id}\"\ntype=\"${compat ? 'optional' : 'incompatible'}\"\nversionRange=\"${range}\"\n"
            }
            ['dev.portalix.neoforge.PortalixNeoForge', 'dev.portalix.neoforge.client.PortalixNeoForgeClient'].each { name ->
                entries[name.replace('.', '/') + '.class'] = 'class'
            }
        }
        entries
    }

    private File archive(String name, Map<String, String> entries) {
        File file = directory.resolve(name).toFile()
        new ZipOutputStream(new FileOutputStream(file)).withCloseable { zip ->
            entries.each { path, contents ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(contents.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        file
    }
}

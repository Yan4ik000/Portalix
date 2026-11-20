import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.util.zip.ZipFile

class SodiumCompileArchive {
    private static final String METADATA = 'META-INF/jarjar/metadata.json'

    static Map single(Collection<File> archives) {
        List<Map> matches = []
        archives.each { archive ->
            new ZipFile(archive).withCloseable { zip ->
                def metadata = zip.getEntry(METADATA)
                if (metadata == null) return
                def jars = zip.getInputStream(metadata).withCloseable { new JsonSlurper().parse(it, 'UTF-8').jars }
                if (!(jars instanceof List)) {
                    throw new GradleException("Invalid JarJar metadata in ${archive}")
                }
                jars.findAll { it.identifier == [group: 'net.caffeinemc', artifact: 'neoforge'] }.each {
                    def path = it.path
                    if (!(path instanceof String) || !path.endsWith('.jar') ||
                        zip.stream().filter { entry -> !entry.directory && entry.name == path }.count() != 1) {
                        throw new GradleException("JarJar entry ${path} in ${archive} must name exactly one nested JAR")
                    }
                    matches.add([archive: archive, path: path])
                }
            }
        }
        if (matches.size() != 1) {
            throw new GradleException("Expected exactly one net.caffeinemc:neoforge declaration in Sodium compile artifacts ${archives}; found ${matches.size()}: ${matches}")
        }
        matches.first()
    }
}

import org.gradle.api.GradleException
import java.util.zip.ZipFile

class SodiumCompileArchive {
    static Map single(Collection<File> archives) {
        List<Map> matches = []
        archives.each { archive ->
            new ZipFile(archive).withCloseable { zip ->
                zip.entries().each { entry ->
                    if (!entry.directory && entry.name ==~ /META-INF\/jarjar\/net\.caffeinemc\.sodium-[^\/]+-mod\.jar/) {
                        matches.add([archive: archive, path: entry.name])
                    }
                }
            }
        }
        if (matches.size() != 1) {
            throw new GradleException("Expected exactly one META-INF/jarjar/net.caffeinemc.sodium-*-mod.jar in Sodium compile artifacts ${archives}; found ${matches.size()}: ${matches}")
        }
        matches.first()
    }
}

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.util.zip.ZipFile

class ModArchiveMetadata {
    static Map<String, String> versions(ZipFile archive, String loader) {
        String path = loader == 'fabric' ? 'fabric.mod.json' : 'META-INF/neoforge.mods.toml'
        def entry = archive.getEntry(path)
        if (entry == null) return [:]
        String text = archive.getInputStream(entry).withCloseable { it.getText('UTF-8') }
        if (loader == 'fabric') {
            def metadata = new JsonSlurper().parseText(text)
            if (!(metadata.id instanceof String) || !(metadata.version instanceof String))
                throw new GradleException("Invalid Fabric mod identity in ${archive.name}")
            return [(metadata.id): metadata.version]
        }
        Map<String, String> result = [:]
        def sections = text =~ /(?ms)^\s*\[\[mods]]\s*\n(.*?)(?=^\s*\[|\z)/
        sections.each { section ->
            String id = stringValue(section[1], 'modId')
            String version = stringValue(section[1], 'version')
            if (result.put(id, version) != null)
                throw new GradleException("Duplicate mod ${id} in ${archive.name}")
        }
        if (!result) throw new GradleException("No NeoForge mod identity in ${archive.name}")
        result
    }

    static String stringValue(String section, String key) {
        def values = section =~ /(?m)^\s*${key}\s*=\s*"([^"\r\n]*)"\s*(?:#.*)?$/
        if (values.size() != 1) throw new GradleException("Expected one literal ${key} in mod metadata")
        values[0][1]
    }
}

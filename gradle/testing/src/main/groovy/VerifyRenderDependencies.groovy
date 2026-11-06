import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

abstract class VerifyRenderDependencies extends DefaultTask {
    @Input abstract Property<String> getLoaderName()
    @Input abstract MapProperty<String, String> getDeclaredVersions()
    @InputFiles @PathSensitive(PathSensitivity.NONE) abstract ConfigurableFileCollection getArtifacts()

    @TaskAction void verify() {
        verify(artifacts.files, loaderName.get(), declaredVersions.get())
    }

    static void verify(Collection<File> artifacts, String loader, Map<String, String> expected) {
        Map<String, List<String>> resolved = expected.collectEntries { id, version -> [(id): []] }
        artifacts.each { file ->
            new ZipFile(file).withCloseable { archive ->
                ModArchiveMetadata.versions(archive, loader).each { id, version ->
                    if (resolved.containsKey(id)) resolved[id].add(version)
                }
            }
        }
        expected.each { id, version ->
            if (resolved[id] != [version]) {
                throw new GradleException("${loader} ${id} compatibility declares ${version}, but resolved artifacts contain ${resolved[id]}; update and validate artifact coordinates and compatibility metadata together")
            }
        }
    }
}

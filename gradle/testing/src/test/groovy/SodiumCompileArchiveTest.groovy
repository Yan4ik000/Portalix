import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import static org.junit.jupiter.api.Assertions.assertThrows

class SodiumCompileArchiveTest {
    @TempDir File directory

    @Test void selectsTheSingleNestedModRatherThanOtherLibraries() {
        String path = 'META-INF/jarjar/net.caffeinemc.sodium-0.8.12-mod.jar'
        File archive = archive('sodium.jar', [path, 'META-INF/jarjar/other.jar', 'net.caffeinemc.sodium-0.8.12-mod.jar'])
        assert SodiumCompileArchive.single([archive]) == [archive: archive, path: path]
    }

    @Test void rejectsMissingOrChangedPackagingWithAnExplicitMatchCount() {
        [[], ['net.caffeinemc.sodium-1-mod.jar'], ['META-INF/jarjar/nested/net.caffeinemc.sodium-1-mod.jar']].eachWithIndex { entries, index ->
            def failure = assertThrows(GradleException) {
                SodiumCompileArchive.single([archive("missing-${index}.jar", entries)])
            }
            assert failure.message.contains('found 0')
        }
        assertThrows(GradleException) { SodiumCompileArchive.single([]) }
    }

    @Test void rejectsAmbiguityWithinOneArchiveAndAcrossResolvedArtifacts() {
        String first = 'META-INF/jarjar/net.caffeinemc.sodium-1-mod.jar'
        String second = 'META-INF/jarjar/net.caffeinemc.sodium-2-mod.jar'
        [[archive('both.jar', [first, second])],
         [archive('one.jar', [first]), archive('two.jar', [second])]].each { artifacts ->
            def failure = assertThrows(GradleException) { SodiumCompileArchive.single(artifacts) }
            assert failure.message.contains('found 2')
            assert failure.message.contains(first)
            assert failure.message.contains(second)
        }
    }

    private File archive(String name, List<String> paths) {
        File archive = new File(directory, name)
        new ZipOutputStream(new FileOutputStream(archive)).withCloseable { zip ->
            paths.each { path ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write([1, 2, 3] as byte[])
                zip.closeEntry()
            }
        }
        archive
    }
}

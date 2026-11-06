import java.nio.file.Path
import org.gradle.api.GradleException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import static org.junit.jupiter.api.Assertions.assertThrows

class RegressionSceneReportsTest {
    @TempDir Path temporary
    private final Map catalog = [scenes: ['camera', 'carry'], benchmarks: ['performance']]
    private Map report

    @BeforeEach void independentSystems() {
        def scenes = catalog.scenes.collect { id -> scene(id) }
        List names = scenes.collectMany { it.passed }
        report = [clientTest: 'all', scenes: scenes, passed: names,
            cases: names.collect { [name: it, status: 'passed'] }]
    }

    @Test void completeSystemsAndOneExplicitlySelectedSystemPass() {
        assert verify() == report.passed
        report.clientTest = 'carry'
        report.scenes = [report.scenes[1]]
        report.passed = report.scenes[0].passed
        report.cases = report.passed.collect { [name: it, status: 'passed'] }
        assert verify('carry') == report.passed
    }

    @Test void selectionCannotSilentlyOmitDuplicateReorderOrSubstituteASystem() {
        def original = report.scenes
        [[original[0]], original.reverse(), original + original[0]].each {
            report.scenes = it
            assertThrows(GradleException) { verify() }
        }
    }

    @Test void eachSystemNeedsItsOwnPassingCasesCleanupAndPresentedEvidence() {
        report.scenes[0].status = 'failed'
        assertThrows(GradleException) { verify() }
        report.scenes[0].status = 'passed'
        def aggregate = report.cases
        report.cases = aggregate.take(1)
        report.passed = report.cases.collect { it.name }
        assertThrows(GradleException) { verify() }
        report.cases = aggregate
        report.passed = aggregate.collect { it.name }
        temporary.resolve('evidence/camera/camera-after.png').toFile().delete()
        assertThrows(GradleException) { verify() }
    }

    private Map scene(String id) {
        File directory = temporary.resolve('evidence/' + id).toFile()
        directory.mkdirs()
        new File(directory, id + '-after.png').bytes = [1] as byte[]
        [id: id, status: 'passed', durationSeconds: 1d, checkedFrames: 1,
            passed: [id + '/gameplay'], cases: [[name: id + '/gameplay', status: 'passed']],
            checkpoints: [id + '-after']]
    }

    private List<String> verify(String selection = 'all') {
        RegressionSceneReports.verify(temporary.toFile(), report, catalog, selection)
    }
}

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import java.time.ZoneId
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import static org.junit.jupiter.api.Assertions.assertThrows

class VerifyGameTestReportTest {
    private static final List<String> SYSTEMS = ['portalix_regression:placement', 'portalix_regression:carry']
    @TempDir Path temporary

    @Test void everySystemPassesInEitherNativeOrNestedJUnitReports() {
        String cases = entries(SYSTEMS)
        VerifyGameTestReport.verifyReport(report("<testsuite>${cases}</testsuite>"), SYSTEMS)
        VerifyGameTestReport.verifyReport(report("<testsuites><testsuite>${cases}</testsuite></testsuites>"), SYSTEMS)
    }

    @Test void failureInOneSystemCannotHideOmittedSystems() {
        assertThrows(GradleException) {
            VerifyGameTestReport.verifyReport(report("<testsuite>${entries(SYSTEMS.take(1))}</testsuite>"), SYSTEMS)
        }
        assertThrows(GradleException) {
            VerifyGameTestReport.verifyReport(report("<testsuite>${entries(SYSTEMS)}<failure/></testsuite>"), SYSTEMS)
        }
    }

    @Test void duplicatesAndUnexpectedTestsCannotReplaceTheRequiredCoverage() {
        assertThrows(GradleException) {
            VerifyGameTestReport.verifyReport(report("<testsuite>${entries(SYSTEMS + SYSTEMS[0])}</testsuite>"), SYSTEMS)
        }
    }

    @Test void absentMalformedAndNonJUnitReportsCannotPass() {
        assertThrows(GradleException) {
            VerifyGameTestReport.verifyReport(temporary.resolve('absent.xml').toFile(), SYSTEMS)
        }
        assertThrows(GradleException) {
            VerifyGameTestReport.verifyReport(report('<not_a_report/>'), SYSTEMS)
        }
    }

    @Test void successfulCasesDoNotExcuseFatalServerLaunchDiagnostics() {
        File log = temporary.resolve('latest.log').toFile()
        long startedAt = LocalDate.of(2025, 1, 10).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        def modified = FileTime.fromMillis(startedAt + 2_000)
        log.setText('[12:00:00] [Server thread/INFO] (Minecraft) Game test server shutting down\n', 'UTF-8')
        Files.setLastModifiedTime(log.toPath(), modified)
        VerifyGameTestReport.verifyLaunchLog(log, startedAt)
        log.append('[12:00:01] [main/ERROR] (Mixin) Could not load plugin\n', 'UTF-8')
        Files.setLastModifiedTime(log.toPath(), modified)
        assertThrows(GradleException) { VerifyGameTestReport.verifyLaunchLog(log, startedAt) }
    }

    private File report(String xml) {
        File file = temporary.resolve('results.xml').toFile()
        file.setText(xml, 'UTF-8')
        file
    }

    private static String entries(List<String> systems) {
        systems.collect { "<testcase name='${it}'/>" }.join()
    }
}

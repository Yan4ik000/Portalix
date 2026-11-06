import java.nio.file.Path
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
        File log = temporary.resolve('server.log').toFile()
        log.text = '[01:02:03] [Server thread/INFO] (Minecraft) Game test server shutting down\n'
        VerifyGameTestReport.verifyLaunchLog(log)
        log.text += '[01:02:04] [main/ERROR] (Mixin) Could not load plugin\n'
        assertThrows(GradleException) { VerifyGameTestReport.verifyLaunchLog(log) }
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

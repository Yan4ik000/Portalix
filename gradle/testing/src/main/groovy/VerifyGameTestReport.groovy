import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.SAXException
import java.time.ZoneId

abstract class VerifyGameTestReport extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) abstract RegularFileProperty getReportFile()
    @InputFile @PathSensitive(PathSensitivity.NONE) abstract RegularFileProperty getLaunchLogFile()
    @InputFile @PathSensitive(PathSensitivity.NONE) abstract RegularFileProperty getLaunchStartFile()
    @Input abstract ListProperty<String> getExpectedTests()
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) abstract ConfigurableFileCollection getTestInstances()

    @TaskAction void verify() {
        verifyReport(reportFile.get().asFile, expectedTests.get())
        def launch = new JsonSlurper().parse(launchStartFile.get().asFile, 'UTF-8')
        if (!(launch instanceof Map) || !(launch.startedAt instanceof Number))
            throw new GradleException('GameTest server produced an invalid launch marker')
        verifyLaunchLog(launchLogFile.get().asFile, launch.startedAt.longValue())
        logger.lifecycle("Server GameTests passed: ${expectedTests.get().join(', ')}")
    }

    static void verifyLaunchLog(File log, Long startedAt = null) {
        if (!log.isFile()) throw new GradleException("GameTest server produced no launch log: ${log}")
        if (startedAt != null && log.lastModified() < startedAt)
            throw new GradleException("GameTest server launch log predates this run: ${log}")
        String text = startedAt == null ? log.getText('UTF-8') :
            VerifyRegressionLog.currentRunLog(log.parentFile, startedAt, ZoneId.systemDefault())
        if (text.isBlank()) throw new GradleException("GameTest server produced an empty launch log: ${log}")
        def fatal = RegressionLogDiagnostics.inspect(text).findAll { it.fatal }
        if (fatal) throw new GradleException("GameTest server launch contains fatal diagnostics: ${fatal.collect { it.category }}; ${log}")
    }

    static void verifyReport(File report, List<String> expected) {
        if (!expected || expected.toSet().size() != expected.size()
            || !expected.every { it ==~ /portalix_regression:[a-z_]+/ })
            throw new GradleException('Expected server GameTests must be distinct system IDs')
        if (!report.isFile()) throw new GradleException("GameTest server produced no report: ${report}")
        def factory = DocumentBuilderFactory.newInstance()
        factory.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
        factory.setXIncludeAware(false)
        def document
        try {
            document = factory.newDocumentBuilder().parse(report)
        } catch (SAXException | IOException failure) {
            throw new GradleException("Cannot parse GameTest report: ${report}", failure)
        }
        if (!(document.documentElement.tagName in ['testsuite', 'testsuites']))
            throw new GradleException("Invalid GameTest report root: ${report}")
        def elements = document.getElementsByTagName('*')
        for (int index = 0; index < elements.length; index++) {
            def entry = elements.item(index)
            if (!(entry.tagName in ['testsuite', 'testsuites'])) continue
            for (String counter : ['failures', 'errors', 'skipped', 'disabled']) {
                if (entry.hasAttribute(counter) && !(entry.getAttribute(counter) ==~ /0+/))
                    throw new GradleException("GameTest report has nonzero or invalid ${counter}: ${report}")
            }
        }
        def cases = document.getElementsByTagName('testcase')
        List<String> names = []
        List<String> failed = []
        for (int index = 0; index < cases.length; index++) {
            def entry = cases.item(index)
            String name = entry.getAttribute('name')
            names.add(name)
            if (entry.getElementsByTagName('failure').length != 0 || entry.getElementsByTagName('error').length != 0
                || entry.getElementsByTagName('skipped').length != 0)
                failed.add(name)
        }
        def missing = expected - names
        def unexpected = names - expected
        def duplicated = names.countBy { it }.findAll { name, count -> count != 1 }.keySet()
        if (missing || unexpected || duplicated || failed)
            throw new GradleException("Server GameTests did not pass: missing=${missing}, unexpected=${unexpected}, duplicated=${duplicated}, failed=${failed}; ${report}")
        for (String marker : ['failure', 'error', 'skipped']) {
            if (document.getElementsByTagName(marker).length != 0)
                throw new GradleException("GameTest report contains ${marker}: ${report}")
        }
    }
}

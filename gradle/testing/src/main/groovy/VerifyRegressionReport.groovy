import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.xml.parsers.DocumentBuilderFactory

abstract class VerifyRegressionReport extends DefaultTask {
    @Input abstract Property<String> getProfileName()
    @Input abstract Property<String> getClientTestSelection()
    @Input abstract Property<String> getDiagnosticsMode()
    @Internal abstract DirectoryProperty getReportDirectory()
    @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getSceneContract()

    @TaskAction void verify() {
        File directory = reportDirectory.get().asFile
        File diagnostics = new File(directory, 'launch-diagnostics.json')
        if (!diagnostics.isFile()) throw new GradleException("Client launch log was not verified: ${diagnostics}")
        def launch = new JsonSlurper().parse(diagnostics, 'UTF-8')
        if (launch.profile == profileName.get() && launch.status == 'unsupported')
            throw new GradleException("Regression profile is unsupported: ${launch.reason}")
        if (launch.profile != profileName.get() || launch.status != 'passed'
            || !(launch.issues instanceof List) || launch.issues.any { it.fatal != false })
            throw new GradleException("Client launch diagnostics did not pass: ${diagnostics}")
        File report = new File(directory, 'results.json')
        if (!report.isFile()) throw new GradleException("Client exited without a regression report: ${report}")
        def result = new JsonSlurper().parse(report, 'UTF-8')
        if (result.profile != profileName.get() || !(result.status in ['passed', 'incomplete']))
            throw new GradleException("Regression profile did not pass: ${report}")
        if (!(result.scenes instanceof List) || result.scenes.any { it.diagnostics != diagnosticsMode.get() })
            throw new GradleException("Regression report did not use the requested diagnostics mode: ${diagnosticsMode.get()}")
        Map contract = new JsonSlurper().parse(sceneContract.get().asFile, 'UTF-8') as Map
        def results = RegressionSceneReports.verify(directory, result, contract, clientTestSelection.get())
        def skipped = results.findAll { it.status == 'skipped' }
        File xml = new File(directory, 'TEST-regression.xml')
        if (!xml.isFile()) throw new GradleException("Client produced no JUnit report: ${xml}")
        def factory = DocumentBuilderFactory.newInstance()
        factory.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
        def document = factory.newDocumentBuilder().parse(xml)
        def suite = document.documentElement
        def cases = document.getElementsByTagName('testcase')
        if (suite.tagName != 'testsuite' || suite.getAttribute('name') != profileName.get()
            || suite.getAttribute('tests') != results.size().toString() || cases.length != results.size()
            || suite.getAttribute('failures') != '0'
            || suite.getAttribute('skipped') != skipped.size().toString()
            || ['errors', 'disabled'].any { suite.hasAttribute(it) && suite.getAttribute(it) != '0' }
            || document.getElementsByTagName('failure').length != 0
            || document.getElementsByTagName('error').length != 0
            || document.getElementsByTagName('skipped').length != skipped.size())
            throw new GradleException("JUnit regression report disagrees with the client results: ${xml}")
        for (int index = 0; index < cases.length; index++) {
            def test = cases.item(index)
            def expected = results[index]
            def skippedElements = test.getElementsByTagName('skipped')
            if (test.getAttribute('name') != expected.name
                || (expected.status == 'skipped'
                    ? skippedElements.length != 1 || skippedElements.item(0).getAttribute('message') != expected.message
                    : skippedElements.length != 0))
                throw new GradleException("JSON and JUnit regression cases disagree: ${xml}")
        }
        if (!skipped.isEmpty()) {
            def summary = skipped.collect { "${it.name}: ${it.message}" }.join('\n')
            throw new GradleException("Regression profile skipped required cases:\n${summary}\nSee ${report}")
        }
        logger.lifecycle("Regression profile passed: ${profileName.get()} (${report})")
    }
}

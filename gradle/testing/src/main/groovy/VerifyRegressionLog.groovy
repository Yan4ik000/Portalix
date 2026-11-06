import groovy.json.JsonOutput
import groovy.json.JsonException
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

abstract class VerifyRegressionLog extends DefaultTask {
    @Input abstract Property<String> getProfileName()
    @Input abstract Property<String> getLoaderName()
    @Input abstract Property<String> getModuleName()
    @Input abstract Property<String> getBackend()
    @Input abstract Property<Boolean> getShaders()
    @Input abstract Property<Boolean> getSupported()
    @Internal abstract DirectoryProperty getRunDirectory()
    @Internal abstract DirectoryProperty getReportDirectory()

    @TaskAction void verify() {
        if (!PrepareRegressionRun.profileMatchesModule(profileName.get(), loaderName.get(), moduleName.get()))
            throw new GradleException("Cannot verify ${profileName.get()} using the ${moduleName.get()} loader task")
        File directory = reportDirectory.get().asFile
        directory.mkdirs()
        File source = new File(runDirectory.get().asFile, 'logs/latest.log')
        File launch = new File(directory, 'launch-start.json')
        if (!supported.get() && PrepareRegressionRun.irisVulkan(profileName.get(), backend.get(), shaders.get())) {
            File preflight = new File(directory, 'results.json')
            if (preflight.isFile()) {
                def result = new JsonSlurper().parse(preflight, 'UTF-8')
                if (result instanceof Map && result.profile == profileName.get() && result.backend == backend.get()
                    && result.shaders == shaders.get() && result.phase == 'preflight' && result.status == 'unsupported'
                    && result.reason == PrepareRegressionRun.IRIS_VULKAN_REASON) {
                    new File(directory, 'launch-diagnostics.json').setText(JsonOutput.prettyPrint(JsonOutput.toJson(
                        [profile: profileName.get(), backend: backend.get(), status: 'unsupported', phase: 'preflight',
                            reason: result.reason, log: null, issues: []])), 'UTF-8')
                    logger.lifecycle("Regression client was not launched: ${result.reason}")
                    return
                }
            }
        }
        List<Map> issues = []
        if (!launch.isFile() || !source.isFile()) {
            issues.add([category: 'missing-launch-log', fatal: true,
                message: 'The current launch did not produce both launch-start.json and logs/latest.log.'])
        } else {
            def started
            try {
                started = new JsonSlurper().parse(launch, 'UTF-8')
            } catch (JsonException failure) {
                issues.add([category: 'invalid-launch-marker', fatal: true, message: failure.message])
            }
            if (!(started instanceof Map) || started.profile != profileName.get() || !(started.startedAt instanceof Number)
                || source.lastModified() < started.startedAt.longValue()) {
                issues.add([category: 'stale-launch-log', fatal: true,
                    message: 'logs/latest.log predates preparation of this exact profile.'])
            } else {
                String text = currentRunLog(source.parentFile, started.startedAt.longValue(), ZoneId.systemDefault())
                issues.addAll(RegressionLogDiagnostics.launchIssues(text, profileName.get(), backend.get()))
                issues.addAll(RegressionLogDiagnostics.inspect(text))
                new File(directory, 'game.log').setText(text, 'UTF-8')
            }
        }
        def failures = issues.findAll { it.fatal }
        new File(directory, 'launch-diagnostics.json').setText(JsonOutput.prettyPrint(JsonOutput.toJson(
            [profile: profileName.get(), status: failures.isEmpty() ? 'passed' : 'failed',
                log: new File(directory, 'game.log').absolutePath,
                issues: issues])), 'UTF-8')
        if (!failures.isEmpty()) {
            def summary = failures.take(8).collect { "${it.category} at line ${it.line ?: '?'}: ${it.message}" }.join('\n')
            throw new GradleException("Regression launch log contains ${failures.size()} fatal diagnostics:\n${summary}\nSee ${directory}/launch-diagnostics.json")
        }
        logger.lifecycle("Regression log passed: ${profileName.get()} (${issues.size()} recorded warnings)")
    }

    static String currentRunLog(File logs, long startedAt, ZoneId zone) {
        File latest = new File(logs, 'latest.log')
        def started = Instant.ofEpochMilli(startedAt).truncatedTo(ChronoUnit.SECONDS)
        LocalDate firstDate = started.atZone(zone).toLocalDate()
        LocalDate lastDate = Instant.ofEpochMilli(latest.lastModified()).atZone(zone).toLocalDate()
        Pattern archive = Pattern.compile(/^(\d{4}-\d{2}-\d{2})-(\d+)\.log\.gz$/)
        Pattern header = Pattern.compile(/^\[([^\]]+)] \[[^\]]+\/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)]/)
        Pattern time = Pattern.compile(/(?:^| )(\d{2}:\d{2}:\d{2})(?:\.\d{1,9})?$/)
        List<Map> sources = []
        for (File file : logs.listFiles() ?: new File[0]) {
            def match = archive.matcher(file.name)
            if (!file.isFile() || !match.matches()) continue
            LocalDate date = LocalDate.parse(match.group(1))
            if (!date.isBefore(firstDate) && !date.isAfter(lastDate))
                sources.add([file: file, date: date, index: new BigInteger(match.group(2))])
        }
        sources.sort { a, b -> a.date <=> b.date ?: a.index <=> b.index }
        sources.add([file: latest, date: lastDate])
        StringBuilder assembled = new StringBuilder()
        boolean included = false
        sources.each { source ->
            def stream = new FileInputStream(source.file as File)
            def input = source.file == latest ? stream : new GZIPInputStream(stream)
            new BufferedReader(new InputStreamReader(input, 'UTF-8')).withCloseable { reader ->
                reader.eachLine { String line ->
                    def record = header.matcher(line)
                    if (record.find()) {
                        def clock = time.matcher(record.group(1))
                        if (!clock.find())
                            throw new GradleException("Cannot date a game log record in ${source.file}: ${line}")
                        def instant = (source.date as LocalDate).atTime(LocalTime.parse(clock.group(1))).atZone(zone).toInstant()
                        included = !instant.isBefore(started)
                    }
                    if (included) assembled.append(line).append('\n')
                }
            }
        }
        assembled.toString()
    }
}

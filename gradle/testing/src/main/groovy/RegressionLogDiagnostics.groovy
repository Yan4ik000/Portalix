import java.util.regex.Pattern

final class RegressionLogDiagnostics {
    private static final Pattern HEADER = Pattern.compile(
        /^\[([^\]]+)] \[([^\]]+)\/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)] (?:\(([^)]*)\)|\[([^\]]*)]):? ?(.*)$/)
    private static final Pattern THROWABLE_HEADER = Pattern.compile(
        /^(?:Exception in thread "[^"]+" )?(?:[A-Za-z_$][\w$]*\.)+[A-Za-z_$][\w$]*(?::.*)?$/)
    private static final Pattern STACK_FRAME = Pattern.compile(/^\s+at [^\s()]+\([^()\r\n]+\)$/)

    static List<Map> launchIssues(String text, String profile, String backend) {
        if (text.isBlank()) return [[category: 'empty-launch-log', fatal: true,
            message: 'The current game log is empty; absence of ERROR lines cannot establish successful log capture.']]
        List<String> messages = text.readLines().findResults { line ->
            def match = HEADER.matcher(line)
            match.matches() && match.group(3) == 'INFO' ? match.group(6) : null
        }
        String graphics = backend == 'opengl' ? 'OpenGL' : backend == 'vulkan' ? 'Vulkan' : null
        List<Map> issues = []
        if (graphics == null || !messages.any { it.startsWith('Using graphics backend ' + graphics + ',') })
            issues.add([category: 'missing-startup-backend', fatal: true,
                message: 'The log does not identify startup of the requested graphics backend: ' + backend])
        Pattern completion = Pattern.compile('(?:\\[STDOUT]: )?PORTALIX REGRESSION '
            + Pattern.quote(profile) + ': (?:passed|failed) \\d+ groups')
        if (!messages.any { completion.matcher(it).matches() })
            issues.add([category: 'missing-profile-completion', fatal: true,
                message: 'The current profile never logged a completed passed/failed client report: ' + profile])
        issues
    }

    static List<Map> inspect(String text) {
        List<Map> events = []
        Map current = null
        text.readLines().eachWithIndex { String line, int index ->
            def match = HEADER.matcher(line)
            if (match.matches()) {
                current = [line: index + 1, level: match.group(3),
                    logger: match.group(4) ?: match.group(5).replaceFirst('/.*$', ''),
                    message: match.group(6), details: []]
                events.add(current)
            } else if (current != null) {
                current.details.add(line)
            } else if (line =~ /(?i)(exception|\berror\b|\bfatal\b)/) {
                events.add([line: index + 1, level: 'ERROR', logger: 'unstructured', message: line, details: []])
            }
        }
        List<Map> issues = []
        events.each { Map event ->
            Map issue = classify(event)
            if (issue != null) issues.add(issue)
            if (issue?.fatal != true) {
                List<String> lines = [event.message] + event.details
                for (int line = 0; line + 1 < lines.size(); line++) {
                    if (THROWABLE_HEADER.matcher(lines[line]).matches()
                        && STACK_FRAME.matcher(lines[line + 1]).matches()) {
                        issues.add([line: event.line + line, level: event.level, logger: event.logger,
                            message: lines[line], category: 'unstructured-exception', fatal: true,
                            reason: 'A JVM throwable header followed by a native stack frame cannot be hidden in an INFO event or its unformatted continuation.',
                            details: lines.drop(line + 1)])
                        break
                    }
                }
            }
        }
        issues
    }

    private static Map classify(Map event) {
        String body = ([event.message] + event.details).join('\n')
        boolean driverError = event.message =~ /^OpenGL debug message: .*\btype=(?:ERROR|UNDEFINED_BEHAVIOR)(?:,|$)/
        boolean exception = body =~ /(?m)(?:^|Caused by: )[^\s]+(?:Exception|Error)(?::|\[)/
        boolean failedOperation = event.level == 'WARN' && event.message =~
            /^(?:Failed to (?:compile|link|load|create)|Could not (?:compile|link|allocate)|Maximum sound pool size |Received passengers for unknown entity)/
        boolean fatal = event.level in ['ERROR', 'FATAL'] || driverError || exception || failedOperation
        if (!fatal && event.level != 'WARN') return null
        [line: event.line, level: event.level, logger: event.logger,
            message: event.message, category: driverError ? 'graphics-driver-error' : fatal ? 'runtime-error' : 'warning',
            fatal: fatal, details: event.details]
    }
}

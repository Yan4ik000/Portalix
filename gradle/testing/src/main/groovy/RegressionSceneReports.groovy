import org.gradle.api.GradleException

final class RegressionSceneReports {
    static List<Map> verify(File directory, Map result, Map catalog, String selection) {
        List selected = selection == 'all' ? catalog.scenes : [selection]
        if (selected.isEmpty() || !selected.every { it in catalog.scenes || it in catalog.benchmarks })
            throw new GradleException("Unknown clientTest '${selection}'")
        if (result.clientTest != selection || !(result.scenes instanceof List)
            || result.scenes.collect { it.id } != selected)
            throw new GradleException('The report does not contain the selected client scenes')
        List<Map> sceneCases = []
        result.scenes.each { Map scene ->
            if (!(scene.durationSeconds instanceof Number)
                || !Double.isFinite(scene.durationSeconds.doubleValue()) || scene.durationSeconds <= 0
                || !(scene.checkedFrames instanceof Number) || scene.checkedFrames <= 0)
                throw new GradleException("Client scene did not finish: ${scene.id}")
            sceneCases.addAll(caseResults(scene))
            if (!(scene.checkpoints instanceof List) || scene.checkpoints.isEmpty())
                throw new GradleException("Client scene produced no screenshots: ${scene.id}")
            scene.checkpoints.each { String checkpoint ->
                if (!(checkpoint ==~ /[A-Za-z0-9][A-Za-z0-9._-]*/))
                    throw new GradleException("Invalid screenshot name: ${checkpoint}")
                File image = new File(directory, "evidence/${scene.id}/${checkpoint}.png")
                if (!image.isFile() || image.length() == 0)
                    throw new GradleException("Missing client screenshot: ${image}")
            }
        }
        List<Map> cases = caseResults(result)
        if (cases.size() < sceneCases.size() || cases.takeRight(sceneCases.size()) != sceneCases)
            throw new GradleException('Aggregate results omit or reorder client scene assertions')
        cases
    }

    private static List<Map> caseResults(Map result) {
        if (!(result.cases instanceof List) || result.cases.isEmpty()
            || !result.cases.every { it instanceof Map && it.name instanceof String && !it.name.isBlank()
                && (it.status == 'passed' || it.status == 'skipped' && it.message instanceof String && !it.message.isBlank()) })
            throw new GradleException('Client assertion results are missing or invalid')
        List<String> names = result.cases.collect { it.name }
        List<String> passed = result.cases.findResults { it.status == 'passed' ? it.name : null }
        String status = passed.size() == names.size() ? 'passed' : 'incomplete'
        if (passed.isEmpty() || result.passed != passed || result.status != status || names.toSet().size() != names.size())
            throw new GradleException('Client assertion results disagree or contain duplicates')
        result.cases
    }
}

import org.junit.jupiter.api.Test

class RegressionLogDiagnosticsTest {
    @Test void everyLoaderErrorIsFatalRegardlessOfPackage() {
        ['[01:02:03] [Render thread/ERROR] (Minecraft) Resource cleanup failed',
         '[01:02:03] [Render thread/ERROR] [another.mod/]: Resource cleanup failed'].each { line ->
            def issues = RegressionLogDiagnostics.inspect(line)
            assert issues.size() == 1 && issues[0].fatal
        }
    }

    @Test void nativeGraphicsErrorsCannotHideInInfoCallbacks() {
        def issues = RegressionLogDiagnostics.inspect('[01:02:03] [Render thread/INFO] (Minecraft) '
            + 'OpenGL debug message: id=2, source=API, type=ERROR, severity=HIGH, message=GL_INVALID_VALUE')
        assert issues.size() == 1 && issues[0].fatal
    }

    @Test void fullLogIncludesLateFailuresAndKeepsOrdinaryWarningsVisible() {
        def issues = RegressionLogDiagnostics.inspect('''[01:02:03] [main/WARN] (FMLConfig) Added missing default option
[01:02:04] [Render thread/INFO] (Minecraft) All regression cases passed
[01:02:05] [Render thread/ERROR] (Portalix) Resource cleanup failed
java.lang.IllegalStateException: leaked texture''')
        assert issues.size() == 2
        assert !issues[0].fatal && issues[1].fatal
    }
}

package work.slhaf.partner.core.action.runner.execution;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.runner.policy.WrappedLaunchSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class CommandExecutionServiceTest {

    private final CommandExecutionService service = CommandExecutionService.INSTANCE;

    @Test
    void testBuildFileExecutionCommandsWithOrderedParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "demo");
        params.put("count", 2);

        String[] commands = service.buildFileExecutionCommands("python3", params, "/tmp/demo.py");

        Assertions.assertArrayEquals(
                new String[]{"python3", "/tmp/demo.py", "--name=demo", "--count=2"},
                commands
        );
    }

    @Test
    void testFileCommandsAndExecution() throws IOException {

        String content = "print(111)";

        Path file = Path.of("/tmp/demo.py");
        Files.writeString(file, "print(111)\n");

        CommandExecutionService.Result catResult = service.exec("cat", "/tmp/demo.py");
        System.out.println(catResult.toString());
        Assertions.assertEquals(content, catResult.getTotal());

        String[] commands = service.buildFileExecutionCommands("python3", null, "/tmp/demo.py");

        CommandExecutionService.Result execResult = service.exec(commands);
        System.out.println(execResult.toString());
        Assertions.assertEquals("111", execResult.getTotal());
    }

    @Test
    void testExecListCollectsStdoutLines() {
        CommandExecutionService.Result result = service.exec(List.of(
                "sh", "-lc", "printf 'hello\\nworld\\n'"
        ));

        Assertions.assertTrue(result.isOk());
        Assertions.assertEquals(List.of("hello", "world"), result.getResultList());
        Assertions.assertEquals(List.of("hello", "world"), result.getStdoutLines());
        Assertions.assertEquals(List.of(), result.getStderrLines());
        Assertions.assertEquals("hello\nworld", result.getTotal());
    }

    @Test
    void testExecVarargsCollectsStdout() {
        CommandExecutionService.Result result = service.exec(
                "sh", "-lc", "printf 'ok'"
        );

        Assertions.assertTrue(result.isOk());
        Assertions.assertEquals(List.of("ok"), result.getResultList());
        Assertions.assertEquals(List.of("ok"), result.getStdoutLines());
        Assertions.assertEquals(List.of(), result.getStderrLines());
        Assertions.assertEquals("ok", result.getTotal());
    }

    @Test
    void testExecReturnsStderrWhenCommandFailsWithoutStdout() {
        CommandExecutionService.Result result = service.exec(
                "sh", "-lc", "echo fail >&2; exit 7"
        );

        Assertions.assertFalse(result.isOk());
        Assertions.assertEquals(List.of("fail"), result.getResultList());
        Assertions.assertEquals(List.of(), result.getStdoutLines());
        Assertions.assertEquals(List.of("fail"), result.getStderrLines());
        Assertions.assertEquals("fail", result.getTotal());
    }

    @Test
    void testExecPrefersStdoutWhenStdoutAndStderrBothExist() {
        CommandExecutionService.Result result = service.exec(
                "sh", "-lc", "echo out; echo err >&2; exit 0"
        );

        Assertions.assertTrue(result.isOk());
        Assertions.assertEquals(List.of("out"), result.getResultList());
        Assertions.assertEquals(List.of("out"), result.getStdoutLines());
        Assertions.assertEquals(List.of("err"), result.getStderrLines());
        Assertions.assertEquals("out\nerr", result.getTotal());
    }

    @Test
    void testCreateSessionTaskCollectsStdoutAndStderr() throws Exception {
        CommandExecutionService.CommandSession session = service.createSessionTask(
                "sh", "-lc", "printf 'hello\\nworld\\n'; printf 'oops\\n' >&2"
        );

        session.getProcess().waitFor();
        waitForBufferContains(session.getStdoutBuffer(), "world");
        waitForBufferContains(session.getStderrBuffer(), "oops");

        Assertions.assertEquals("hello\nworld", session.getStdoutBuffer().toString());
        Assertions.assertEquals("oops", session.getStderrBuffer().toString());
    }

    @Test
    void testExecWrappedLaunchSpecAppliesWorkingDirectory(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        CommandExecutionService.Result result = service.exec(new WrappedLaunchSpec(
                "sh",
                List.of("-lc", "pwd"),
                tempDir.toString(),
                System.getenv()
        ));

        Assertions.assertTrue(result.isOk());
        Assertions.assertEquals(tempDir.toString(), result.getTotal());
    }

    @Test
    void testExecWrappedLaunchSpecAppliesEnvironmentOverride() {
        CommandExecutionService.Result result = service.exec(new WrappedLaunchSpec(
                "sh",
                List.of("-lc", "printf '%s' \"$PARTNER_TEST_ENV\""),
                null,
                Map.of("PARTNER_TEST_ENV", "applied")
        ));

        Assertions.assertTrue(result.isOk());
        Assertions.assertEquals("applied", result.getTotal());
    }

    private void waitForBufferContains(StringBuilder buffer, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            synchronized (buffer) {
                if (buffer.toString().contains(expected)) {
                    return;
                }
            }
            Thread.sleep(20);
        }
        Assertions.fail("buffer did not contain expected text: " + expected);
    }
}

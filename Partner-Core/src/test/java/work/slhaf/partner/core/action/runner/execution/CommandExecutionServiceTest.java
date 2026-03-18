package work.slhaf.partner.core.action.runner.execution;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        Assertions.assertEquals("hello\nworld", result.getTotal());
    }

    @Test
    void testExecVarargsCollectsStdout() {
        CommandExecutionService.Result result = service.exec(
                "sh", "-lc", "printf 'ok'"
        );

        Assertions.assertTrue(result.isOk());
        Assertions.assertEquals(List.of("ok"), result.getResultList());
        Assertions.assertEquals("ok", result.getTotal());
    }

    @Test
    void testExecReturnsStderrWhenCommandFailsWithoutStdout() {
        CommandExecutionService.Result result = service.exec(
                "sh", "-lc", "echo fail >&2; exit 7"
        );

        Assertions.assertFalse(result.isOk());
        Assertions.assertEquals(List.of("fail"), result.getResultList());
        Assertions.assertEquals("fail", result.getTotal());
    }

    @Test
    void testExecPrefersStdoutWhenStdoutAndStderrBothExist() {
        CommandExecutionService.Result result = service.exec(
                "sh", "-lc", "echo out; echo err >&2; exit 0"
        );

        Assertions.assertTrue(result.isOk());
        Assertions.assertEquals(List.of("out"), result.getResultList());
        Assertions.assertEquals("out", result.getTotal());
    }
}

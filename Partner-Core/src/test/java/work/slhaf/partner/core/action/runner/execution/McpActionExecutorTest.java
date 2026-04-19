package work.slhaf.partner.core.action.runner.execution;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.runner.mcp.McpClientRegistry;

import java.lang.reflect.Field;
import java.util.Map;

class McpActionExecutorTest {

    @Test
    void testRunReturnsFailureWhenClientThrows() {
        McpClientRegistry clientRegistry = new McpClientRegistry();
        clientRegistry.register("broken", buildThrowingMcpClient());
        McpActionExecutor executor = new McpActionExecutor(clientRegistry);

        MetaAction metaAction = new MetaAction("demo-tool", false, null, MetaAction.Type.MCP, "broken");
        metaAction.getParams().putAll(Map.of("value", "demo"));

        var response = executor.run(metaAction);

        Assertions.assertFalse(response.isOk());
        Assertions.assertTrue(response.getData().startsWith("MCP tool call failed:"));
    }

    private io.modelcontextprotocol.client.McpSyncClient buildThrowingMcpClient() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            return (io.modelcontextprotocol.client.McpSyncClient) unsafe.allocateInstance(io.modelcontextprotocol.client.McpSyncClient.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to build throwing mcp client", e);
        }
    }
}

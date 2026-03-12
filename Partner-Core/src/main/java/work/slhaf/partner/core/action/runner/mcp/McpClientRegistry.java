package work.slhaf.partner.core.action.runner.mcp;

import io.modelcontextprotocol.client.McpSyncClient;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class McpClientRegistry implements AutoCloseable {

    private final ConcurrentHashMap<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    public McpSyncClient get(String serverName) {
        return clients.get(serverName);
    }

    public void register(String serverName, McpSyncClient client) {
        McpSyncClient old = clients.put(serverName, client);
        if (old != null && old != client) {
            old.close();
        }
    }

    public McpSyncClient remove(String serverName) {
        McpSyncClient client = detach(serverName);
        if (client != null) {
            client.close();
        }
        return client;
    }

    public McpSyncClient detach(String serverName) {
        return clients.remove(serverName);
    }

    public boolean contains(String serverName) {
        return clients.containsKey(serverName);
    }

    public Set<String> listIds() {
        return new HashSet<>(clients.keySet());
    }

    @Override
    public void close() {
        clients.forEach((id, client) -> client.close());
        clients.clear();
    }
}

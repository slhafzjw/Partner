package work.slhaf.partner.core.action.runner;

import io.modelcontextprotocol.client.McpSyncClient;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class McpClientRegistry implements AutoCloseable {

    private final ConcurrentHashMap<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    McpSyncClient get(String serverName) {
        return clients.get(serverName);
    }

    void register(String serverName, McpSyncClient client) {
        McpSyncClient old = clients.put(serverName, client);
        if (old != null && old != client) {
            old.close();
        }
    }

    McpSyncClient remove(String serverName) {
        McpSyncClient client = detach(serverName);
        if (client != null) {
            client.close();
        }
        return client;
    }

    McpSyncClient detach(String serverName) {
        return clients.remove(serverName);
    }

    boolean contains(String serverName) {
        return clients.containsKey(serverName);
    }

    Set<String> listIds() {
        return new HashSet<>(clients.keySet());
    }

    @Override
    public void close() {
        clients.forEach((id, client) -> client.close());
        clients.clear();
    }
}

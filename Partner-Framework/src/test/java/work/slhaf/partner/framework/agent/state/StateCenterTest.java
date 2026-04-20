package work.slhaf.partner.framework.agent.state;

import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.framework.agent.config.ConfigCenter;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateCenterTest {

    @SuppressWarnings("unchecked")
    private static void clearRegistry() throws Exception {
        Field registryField = StateCenter.class.getDeclaredField("stateRegistry");
        registryField.setAccessible(true);
        ConcurrentHashMap<Path, StateRecord> registry = (ConcurrentHashMap<Path, StateRecord>) registryField.get(StateCenter.INSTANCE);
        registry.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        clearRegistry();
    }

    @Test
    void shouldCreateStateFileOnFirstSaveWhenNoExistingFile() throws Exception {
        Path relativePath = Path.of("state-center-test", UUID.randomUUID() + ".json");
        Path finalPath = ConfigCenter.INSTANCE.getPaths().getStateDir().resolve(relativePath).normalize();
        Files.deleteIfExists(finalPath);

        StubStateSerializable serializable = new StubStateSerializable(relativePath, false, "fresh");
        serializable.register();
        StateCenter.INSTANCE.save();

        assertTrue(Files.exists(finalPath));
        JSONObject saved = JSONObject.parseObject(Files.readString(finalPath, StandardCharsets.UTF_8));
        assertEquals("fresh", saved.getString("value"));
    }

    @Test
    void shouldNotOverwriteExistingFileUntilManualLoadWhenAutoLoadDisabled() throws Exception {
        Path relativePath = Path.of("state-center-test", UUID.randomUUID() + ".json");
        Path finalPath = ConfigCenter.INSTANCE.getPaths().getStateDir().resolve(relativePath).normalize();
        Files.createDirectories(finalPath.getParent());
        Files.writeString(finalPath, "{\"value\":\"original\"}", StandardCharsets.UTF_8);

        StubStateSerializable serializable = new StubStateSerializable(relativePath, false, "new-value");
        serializable.register();
        StateCenter.INSTANCE.save();

        JSONObject untouched = JSONObject.parseObject(Files.readString(finalPath, StandardCharsets.UTF_8));
        assertEquals("original", untouched.getString("value"));

        serializable.load();
        serializable.value = "after-load";
        StateCenter.INSTANCE.save();

        JSONObject saved = JSONObject.parseObject(Files.readString(finalPath, StandardCharsets.UTF_8));
        assertEquals("after-load", saved.getString("value"));
    }

    private static final class StubStateSerializable implements StateSerializable {
        private final Path statePath;
        private final boolean autoLoadOnRegister;
        private String value;

        private StubStateSerializable(Path statePath, boolean autoLoadOnRegister, String value) {
            this.statePath = statePath;
            this.autoLoadOnRegister = autoLoadOnRegister;
            this.value = value;
        }

        @Override
        public @NotNull Path statePath() {
            return statePath;
        }

        @Override
        public void load(JSONObject state) {
            value = state.getString("value");
        }

        @Override
        public @NotNull State convert() {
            State state = new State();
            state.append("value", StateValue.Companion.str(value));
            return state;
        }

        @Override
        public boolean autoLoadOnRegister() {
            return autoLoadOnRegister;
        }
    }
}

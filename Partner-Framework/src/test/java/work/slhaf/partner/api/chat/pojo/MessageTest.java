package work.slhaf.partner.api.chat.pojo;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.api.chat.runtime.OpenAiMessageAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageTest {

    @Test
    void shouldSerializeRoleAsProtocolValue() {
        String json = JSON.toJSONString(new Message(Message.Character.USER, "hello"));

        assertEquals("{\"content\":\"hello\",\"role\":\"user\"}", json);
    }

    @Test
    void shouldDeserializeRoleFromProtocolValue() {
        Message message = JSON.parseObject("{\"role\":\"assistant\",\"content\":\"ok\"}", Message.class);

        assertEquals(Message.Character.ASSISTANT, message.getRole());
        assertEquals("assistant", message.roleValue());
    }

    @Test
    void shouldRejectUnsupportedRole() {
        assertThrows(IllegalArgumentException.class, () -> Message.Character.fromValue("tool"));
    }

    @Test
    void shouldAdaptAllSupportedRoles() {
        OpenAiMessageAdapter.toParam(new Message(Message.Character.USER, "u"));
        OpenAiMessageAdapter.toParam(new Message(Message.Character.SYSTEM, "s"));
        OpenAiMessageAdapter.toParam(new Message(Message.Character.ASSISTANT, "a"));
    }
}

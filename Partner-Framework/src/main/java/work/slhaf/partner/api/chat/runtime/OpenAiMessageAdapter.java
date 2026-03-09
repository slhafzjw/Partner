package work.slhaf.partner.api.chat.runtime;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.ArrayList;
import java.util.List;

public final class OpenAiMessageAdapter {

    private OpenAiMessageAdapter() {
    }

    public static List<ChatCompletionMessageParam> toParams(List<Message> messages) {
        List<ChatCompletionMessageParam> params = new ArrayList<>(messages.size());
        for (Message message : messages) {
            params.add(toParam(message));
        }
        return params;
    }

    public static ChatCompletionMessageParam toParam(Message message) {
        return switch (message.getRole()) {
            case SYSTEM -> ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder().content(message.getContent()).build()
            );
            case ASSISTANT -> ChatCompletionMessageParam.ofAssistant(
                    ChatCompletionAssistantMessageParam.builder().content(message.getContent()).build()
            );
            case USER -> ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder().content(message.getContent()).build()
            );
        };
    }
}

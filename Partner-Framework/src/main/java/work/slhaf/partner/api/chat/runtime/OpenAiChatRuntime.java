package work.slhaf.partner.api.chat.runtime;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.*;
import work.slhaf.partner.api.chat.pojo.Message;

import java.time.Duration;
import java.util.List;

public class OpenAiChatRuntime {

    private final OpenAIClient client;
    private final String model;

    public OpenAiChatRuntime(String baseUrl, String apikey, String model) {
        this.client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apikey)
                .timeout(Duration.ofSeconds(30))
                .build();
        this.model = model;
    }

    public String chat(List<Message> messages) {
        ChatCompletionCreateParams params = buildParams(messages);
        return extractText(client.chat().completions().create(params));
    }

    public void streamChat(List<Message> messages, StreamChatMessageConsumer handler) {
        ChatCompletionCreateParams params = buildParams(messages);
        try (StreamResponse<ChatCompletionChunk> streamResponse = client.chat().completions().createStreaming(params)) {
            streamResponse.stream()
                    .flatMap(completion -> completion.choices().stream())
                    .flatMap(choice -> choice.delta().content().stream())
                    .filter(delta -> !delta.isEmpty())
                    .forEach(handler::onDelta);
        }
    }

    public <T> T formattedChat(List<Message> messages, Class<T> responseType) {
        StructuredChatCompletionCreateParams<T> params = buildParams(messages).toBuilder()
                .responseFormat(responseType)
                .build();
        return extractStructured(client.chat().completions().create(params));
    }

    private ChatCompletionCreateParams buildParams(List<Message> messages) {
        return ChatCompletionCreateParams.builder()
                .model(model)
                .messages(OpenAiMessageAdapter.toParams(messages))
                .build();
    }

    private String extractText(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI chat completion returned no choices.");
        }
        return completion.choices().getFirst().message().content()
                .orElseThrow(() -> new IllegalStateException("OpenAI chat completion returned empty content."));
    }

    private <T> T extractStructured(StructuredChatCompletion<T> completion) {
        if (completion.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI structured chat completion returned no choices.");
        }
        return completion.choices().getFirst().message().content()
                .orElseThrow(() -> new IllegalStateException("OpenAI structured chat completion returned empty content."));
    }
}

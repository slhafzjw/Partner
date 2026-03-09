package work.slhaf.partner.api.chat.runtime;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
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

    public String chat(List<Message> messages, boolean streaming) {
        ChatCompletionCreateParams params = buildParams(messages);
        if (!streaming) {
            return extractText(client.chat().completions().create(params));
        }

        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
        try (StreamResponse<ChatCompletionChunk> response = client.chat().completions().createStreaming(params)) {
            response.stream().forEach(accumulator::accumulate);
        }
        return extractText(accumulator.chatCompletion());
    }

    public <T> T formattedChat(List<Message> messages, boolean streaming, Class<T> responseType) {
        StructuredChatCompletionCreateParams<T> params = buildParams(messages).toBuilder()
                .responseFormat(responseType)
                .build();
        if (!streaming) {
            return extractStructured(client.chat().completions().create(params));
        }

        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
        try (StreamResponse<ChatCompletionChunk> response = client.chat().completions().createStreaming(params.rawParams())) {
            response.stream().forEach(accumulator::accumulate);
        }
        return extractStructured(accumulator.chatCompletion(responseType));
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

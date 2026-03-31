package work.slhaf.partner.api.chat.provider.openai;

import com.alibaba.fastjson2.JSONObject;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.*;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.chat.StreamChatMessageConsumer;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.provider.ModelProvider;
import work.slhaf.partner.api.chat.provider.ProviderOverride;

import java.time.Duration;
import java.util.List;

public class OpenAiCompatibleProvider extends ModelProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    private final OpenAIClient client;

    public OpenAiCompatibleProvider(String baseUrl, String apikey, String model) {
        this.client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apikey)
                .timeout(Duration.ofSeconds(30))
                .build();
        this.baseUrl = baseUrl;
        this.apiKey = apikey;
        this.model = model;
    }

    public OpenAiCompatibleProvider(String baseUrl, String apikey, String model, ProviderOverride override) {
        super(override);
        this.client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apikey)
                .timeout(Duration.ofSeconds(30))
                .build();
        this.baseUrl = baseUrl;
        this.apiKey = apikey;
        this.model = model;
    }

    public @NotNull String chat(@NotNull List<Message> messages) {
        ChatCompletionCreateParams params = buildParams(messages);
        return extractText(client.chat().completions().create(params));
    }

    public void streamChat(@NotNull List<Message> messages, StreamChatMessageConsumer handler) {
        ChatCompletionCreateParams params = buildParams(messages);
        try (StreamResponse<ChatCompletionChunk> streamResponse = client.chat().completions().createStreaming(params)) {
            streamResponse.stream()
                    .flatMap(completion -> completion.choices().stream())
                    .flatMap(choice -> choice.delta().content().stream())
                    .filter(delta -> !delta.isEmpty())
                    .forEach(handler::onDelta);
        }
    }

    public <T> T formattedChat(@NotNull List<Message> messages, @NotNull Class<T> responseType) {
        StructuredChatCompletionCreateParams<T> params = buildParams(messages).toBuilder()
                .responseFormat(responseType)
                .build();
        return extractStructured(client.chat().completions().create(params));
    }

    private ChatCompletionCreateParams buildParams(List<Message> messages) {
        ChatCompletionCreateParams.Builder paramsBuilder = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(OpenAiMessageAdapter.toParams(messages));

        ProviderOverride override = getOverride();
        if (override != null) {
            if (override.getTemperature() != null) {
                paramsBuilder.temperature(override.getTemperature());
            }
            if (override.getTopP() != null) {
                paramsBuilder.topP(override.getTopP());
            }
            if (override.getMaxTokens() != null) {
                paramsBuilder.maxCompletionTokens(override.getMaxTokens());
            }
            JSONObject extras = override.getExtras();
            if (extras != null) {
                extras.forEach((key, value) -> {
                    paramsBuilder.putAdditionalBodyProperty(key, JsonValue.from(value));
                });
            }
        }

        return paramsBuilder.build();
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

    @Override
    public @NotNull ModelProvider fork(@NotNull ProviderOverride override) {
        return new OpenAiCompatibleProvider(baseUrl, apiKey, override.getModel(), override);
    }
}

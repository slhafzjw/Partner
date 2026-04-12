package work.slhaf.partner.framework.agent.model.provider.openai;

import com.alibaba.fastjson2.JSONObject;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.*;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ModelInvokeException;
import work.slhaf.partner.framework.agent.model.StreamChatMessageConsumer;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.model.provider.ModelProvider;
import work.slhaf.partner.framework.agent.model.provider.ProviderOverride;
import work.slhaf.partner.framework.agent.support.Result;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleProvider extends ModelProvider {

    private static final int MAX_ATTEMPTS = 3;

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    private final OpenAIClient client;

    public OpenAiCompatibleProvider(String providerName, String modelKey, String baseUrl, String apikey, String model) {
        super(providerName, modelKey, null);
        this.client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apikey)
                .timeout(Duration.ofSeconds(30))
                .build();
        this.baseUrl = baseUrl;
        this.apiKey = apikey;
        this.model = model;
    }

    public OpenAiCompatibleProvider(String providerName, String modelKey, String baseUrl, String apikey, String model, ProviderOverride override) {
        super(providerName, modelKey, override);
        this.client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apikey)
                .timeout(Duration.ofSeconds(30))
                .build();
        this.baseUrl = baseUrl;
        this.apiKey = apikey;
        this.model = model;
    }

    @Override
    public @NotNull Result<String> chat(@NotNull List<Message> messages) {
        return executeWithRetry(
                "OpenAI-compatible provider failed to complete the chat request after 3 attempts.",
                () -> extractText(client.chat().completions().create(buildParams(messages)))
        );
    }

    @Override
    public @NotNull Result<Unit> streamChat(@NotNull List<Message> messages, @NotNull StreamChatMessageConsumer handler) {
        Exception lastFailure = null;
        int remainingAttempts = MAX_ATTEMPTS;
        while (remainingAttempts > 0) {
            boolean emitted = false;
            try (StreamResponse<ChatCompletionChunk> streamResponse = client.chat().completions().createStreaming(buildParams(messages))) {
                Iterator<ChatCompletionChunk> iterator = streamResponse.stream().iterator();
                while (iterator.hasNext()) {
                    ChatCompletionChunk chunk = iterator.next();
                    for (ChatCompletionChunk.Choice choice : chunk.choices()) {
                        String delta = choice.delta().content().orElse("");
                        if (delta.isEmpty()) {
                            continue;
                        }
                        emitted = true;
                        handler.onDelta(delta);
                    }
                }
                return Result.success(Unit.INSTANCE);
            } catch (Exception e) {
                lastFailure = e;
                remainingAttempts--;
                if (emitted || remainingAttempts == 0) {
                    break;
                }
            }
        }
        return Result.failure(invokeException(
                "OpenAI-compatible provider failed to stream the chat response after 3 attempts.",
                lastFailure
        ));
    }

    @Override
    public <T> @NotNull Result<T> formattedChat(@NotNull List<Message> messages, @NotNull Class<T> responseType) {
        return executeWithRetry(
                "OpenAI-compatible provider failed to complete the structured chat request after 3 attempts.",
                () -> {
                    StructuredChatCompletionCreateParams<T> params = buildParams(messages).toBuilder()
                            .responseFormat(responseType)
                            .build();
                    return extractStructured(client.chat().completions().create(params));
                }
        );
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
                extras.forEach((key, value) -> paramsBuilder.putAdditionalBodyProperty(key, JsonValue.from(value)));
            }
        }

        return paramsBuilder.build();
    }

    private String extractText(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            throw invokeException("OpenAI chat completion returned no choices.", null);
        }
        return completion.choices().getFirst().message().content()
                .orElseThrow(() -> invokeException("OpenAI chat completion returned empty content.", null));
    }

    private <T> T extractStructured(StructuredChatCompletion<T> completion) {
        if (completion.choices().isEmpty()) {
            throw invokeException("OpenAI structured chat completion returned no choices.", null);
        }
        return completion.choices().getFirst().message().content()
                .orElseThrow(() -> invokeException("OpenAI structured chat completion returned empty content.", null));
    }

    @Override
    public @NotNull ModelProvider fork(@NotNull String modelKey, ProviderOverride override) {
        if (override == null) {
            return new OpenAiCompatibleProvider(getProviderName(), modelKey, baseUrl, apiKey, model, getOverride());
        }
        return new OpenAiCompatibleProvider(getProviderName(), modelKey, baseUrl, apiKey, override.getModel(), override);
    }

    private <T> Result<T> executeWithRetry(String failureMessage, ThrowingSupplier<T> supplier) {
        AgentRuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Result<T> result = Result.runCatching(supplier::get);
            AgentRuntimeException failure = result.exceptionOrNull();
            if (failure == null) {
                return result;
            }
            lastFailure = failure;
        }
        return Result.failure(invokeException(failureMessage, lastFailure));
    }

    private ModelInvokeException invokeException(String message, Throwable cause) {
        return new ModelInvokeException(
                message,
                getProviderName(),
                getModelKey(),
                baseUrl,
                model,
                getOverride() == null ? Map.of() : toOverrideReport(getOverride()),
                cause
        );
    }

    private Map<String, String> toOverrideReport(ProviderOverride override) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("model", override.getModel());
        if (override.getTemperature() != null) {
            result.put("temperature", override.getTemperature().toString());
        }
        if (override.getTopP() != null) {
            result.put("topP", override.getTopP().toString());
        }
        if (override.getMaxTokens() != null) {
            result.put("maxTokens", override.getMaxTokens().toString());
        }
        JSONObject extras = override.getExtras();
        if (extras != null) {
            extras.forEach((key, value) -> result.put("extra." + key, value == null ? "null" : value.toString()));
        }
        return result;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

package work.slhaf.partner.api.chat;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.chat.constant.ChatConstant;
import work.slhaf.partner.api.chat.pojo.ChatBody;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.PrimaryChatResponse;

import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
public class ChatClient {
    private String clientId;

    private String url;
    private String apikey;
    private String model;

    private double top_p;
    private double temperature;
    private int max_tokens;

    public ChatClient(String url, String apikey, String model) {
        this.url = url;
        this.apikey = apikey;
        this.model = model;
    }

    public ChatResponse runChat(List<Message> messages) {
        HttpRequest request = HttpRequest.post(url);
        request.setConnectionTimeout(2000);
        request.setReadTimeout(15000);
        request.header("Content-Type", "application/json");
        request.header("Authorization", "Bearer " + apikey);

        ChatBody body;
        if (top_p > 0) {
            body = ChatBody.builder()
                    .model(model)
                    .messages(messages)
                    .top_p(top_p)
                    .temperature(temperature)
                    .max_tokens(max_tokens)
                    .build();
        } else {
            body = ChatBody.builder()
                    .model(model)
                    .messages(messages)
                    .build();
        }

        ChatResponse finalResponse;

        try {
            HttpResponse response = request.body(JSONUtil.toJsonStr(body)).execute();
            PrimaryChatResponse primaryChatResponse = JSONUtil.toBean(response.body(), PrimaryChatResponse.class);
            finalResponse = ChatResponse.builder()
                    .status(ChatConstant.ResponseStatus.SUCCESS)
                    .message(primaryChatResponse.getChoices().get(0).getMessage().getContent())
                    .usageBean(primaryChatResponse.getUsage())
                    .build();

            response.close();
        } catch (IORuntimeException e) {
            log.error("请求超时", e);
            finalResponse = ChatResponse.builder()
                    .message("连接超时")
                    .status(ChatConstant.ResponseStatus.FAILED)
                    .usageBean(null)
                    .build();
        }
        return finalResponse;
    }

}

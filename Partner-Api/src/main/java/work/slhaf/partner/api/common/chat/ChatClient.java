package work.slhaf.partner.api.common.chat;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import work.slhaf.partner.api.common.chat.constant.ChatConstant;
import work.slhaf.partner.api.common.chat.pojo.ChatBody;
import work.slhaf.partner.api.common.chat.pojo.ChatResponse;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.common.chat.pojo.PrimaryChatResponse;

import java.util.List;

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

        HttpResponse response = request.body(JSONUtil.toJsonStr(body)).execute();
        ChatResponse finalResponse;

        PrimaryChatResponse primaryChatResponse = JSONUtil.toBean(response.body(), PrimaryChatResponse.class);
        finalResponse = ChatResponse.builder()
                .type(ChatConstant.Response.SUCCESS)
                .message(primaryChatResponse.getChoices().get(0).getMessage().getContent())
                .usageBean(primaryChatResponse.getUsage())
                .build();

        response.close();
        return finalResponse;
    }

}

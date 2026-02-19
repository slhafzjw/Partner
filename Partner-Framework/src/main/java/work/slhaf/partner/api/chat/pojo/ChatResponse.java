package work.slhaf.partner.api.chat.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import work.slhaf.partner.api.chat.constant.ChatConstant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private ChatConstant.ResponseStatus status;
    private String message;
    private PrimaryChatResponse.UsageBean usageBean;
}

package work.slhaf.partner.api.common.chat.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private String type;
    private String message;
    private PrimaryChatResponse.UsageBean usageBean;
}

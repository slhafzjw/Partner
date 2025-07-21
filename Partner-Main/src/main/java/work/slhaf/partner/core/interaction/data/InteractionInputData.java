package work.slhaf.partner.core.interaction.data;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InteractionInputData {
    private String userInfo;
    private String userNickName;
    private String content;
    private LocalDateTime localDateTime;
    private String platform;
    private boolean single;
}

package work.slhaf.agent.common.chat.pojo;

import lombok.*;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatBody {
    @NonNull
    private String model;
    @NonNull
    private List<Message> messages;
    @Builder.Default
    private int temperature = 1;
    @Builder.Default
    private int top_p = 1;
    private boolean stream;
    @Builder.Default
    private int max_tokens = 1024;
    private int presence_penalty;
    private int frequency_penalty;
}

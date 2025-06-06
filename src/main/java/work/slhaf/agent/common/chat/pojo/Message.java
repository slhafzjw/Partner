package work.slhaf.agent.common.chat.pojo;

import lombok.*;
import work.slhaf.agent.common.serialize.PersistableObject;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    @NonNull
    private String role;
    @NonNull
    private String content;
}

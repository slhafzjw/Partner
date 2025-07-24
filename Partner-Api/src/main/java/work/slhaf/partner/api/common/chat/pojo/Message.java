package work.slhaf.partner.api.common.chat.pojo;

import lombok.*;
import work.slhaf.partner.api.common.entity.PersistableObject;

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

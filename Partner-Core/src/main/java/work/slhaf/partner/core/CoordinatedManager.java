package work.slhaf.partner.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CoordinateManager;
import work.slhaf.partner.api.agent.factory.capability.annotation.Coordinated;
import work.slhaf.partner.api.chat.constant.ChatConstant;
import work.slhaf.partner.core.cognation.CognationCore;
import work.slhaf.partner.core.memory.MemoryCore;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import static work.slhaf.partner.common.util.ExtractUtil.extractUserId;

@Data
@Slf4j
@CoordinateManager
public class CoordinatedManager implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //在框架将自动注入core,详见CapabilityRegistryFactory
    private CognationCore cognationCore;
    private MemoryCore memoryCore;


    private boolean isCacheSingleUser() {
        return memoryCore.getUserDialogMap().size() <= 1;
    }

    @Coordinated(capability = "cognation")
    public boolean isSingleUser() {
        return isCacheSingleUser() && isChatMessagesSingleUser();
    }

    private boolean isChatMessagesSingleUser() {
        Set<String> userIdSet = new HashSet<>();
        cognationCore.getChatMessages().forEach(m -> {
            if (m.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                return;
            }
            String userId = extractUserId(m.getContent());
            if (userId == null || userId.isEmpty()) {
                return;
            }
            userIdSet.add(userId);
        });
        return userIdSet.size() <= 1;
    }


}

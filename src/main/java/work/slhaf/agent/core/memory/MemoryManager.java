package work.slhaf.agent.core.memory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.User;
import work.slhaf.agent.modules.memory.SliceEvaluator;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Slf4j
public class MemoryManager implements InteractionModule {

    private static MemoryManager memoryManager;

    private MemoryGraph memoryGraph;
    private SliceEvaluator sliceEvaluator;

    private MemoryManager(){}

    @Override
    public void execute(InteractionContext interactionContext) {

    }

    public static MemoryManager getInstance() throws IOException, ClassNotFoundException {
        if (memoryManager == null) {
            Config config = Config.getConfig();
            memoryManager = new MemoryManager();
            memoryManager.setMemoryGraph(MemoryGraph.getInstance(config.getAgentId()));
            memoryManager.setSliceEvaluator(SliceEvaluator.getInstance());
            log.info("MemoryManager注册完毕...");
        }
        return memoryManager;
    }

    public MemoryResult selectMemory(String path) throws IOException, ClassNotFoundException {
        return memoryGraph.selectMemory(path);
    }

    public MemoryResult selectMemory(LocalDate date) {
        return memoryGraph.selectMemory(date);
    }

    public String getUserId(String userInfo,String nickName) {
        String userId = null;
        for (User user : memoryGraph.getUsers()) {
            if (user.getInfo().contains(userInfo)){
                userId = user.getUuid();
            }
        }
        if (userId == null) {
            User newUser = setNewUser(userInfo, nickName);
            memoryGraph.getUsers().add(newUser);
            userId = newUser.getUuid();
        }
        return userId;
    }

    private static User setNewUser(String userInfo, String nickName) {
        User newUser = new User();
        newUser.setUuid(UUID.randomUUID().toString());
        List<String> infoList = new ArrayList<>();
        infoList.add(userInfo);
        newUser.setInfo(infoList);
        newUser.setNickName(nickName);
        return newUser;
    }

}

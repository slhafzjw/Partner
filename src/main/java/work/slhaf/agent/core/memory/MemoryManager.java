package work.slhaf.agent.core.memory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.core.memory.pojo.User;
import work.slhaf.agent.shared.memory.EvaluatedSlice;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
@Slf4j
public class MemoryManager implements InteractionModule {

    private static MemoryManager memoryManager;
    private final Lock sliceInsertLock = new ReentrantLock();
    private final Lock messageCleanLock = new ReentrantLock();

    private MemoryGraph memoryGraph;
    private HashMap<String, List<EvaluatedSlice>> activatedSlices;

    private MemoryManager() {
    }

    @Override
    public void execute(InteractionContext interactionContext) {

    }

    public static MemoryManager getInstance() throws IOException, ClassNotFoundException {
        if (memoryManager == null) {
            Config config = Config.getConfig();
            memoryManager = new MemoryManager();
            memoryManager.setMemoryGraph(MemoryGraph.getInstance(config.getAgentId()));
            memoryManager.setActivatedSlices(new HashMap<>());
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

    public void cleanSelectedSliceFilter() {
        memoryGraph.getSelectedSlices().clear();
    }

    public String getUserId(String userInfo, String nickName) {
        String userId = null;
        for (User user : memoryGraph.getUsers()) {
            if (user.getInfo().contains(userInfo)) {
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

    public List<Message> getChatMessages() {
        return memoryGraph.getChatMessages();
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

    public String getTopicTree() {
        return memoryGraph.getTopicTree();
    }

    public ConcurrentHashMap<String, String> getStaticMemory(String userId) {
        return memoryGraph.getStaticMemory().get(userId);
    }

    public HashMap<LocalDateTime, String> getDialogMap() {
        return memoryGraph.getDialogMap();
    }

    public ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId) {
        return memoryGraph.getUserDialogMap().get(userId);
    }

    public String getCharacter() {
        return memoryGraph.getCharacter();
    }

    public void resetMemoryId() {
        memoryGraph.setMemoryId(UUID.randomUUID().toString());
    }

    public String getMemoryId() {
        return memoryGraph.getMemoryId();
    }

    public void insertSlice(MemorySlice memorySlice, String topicPath) throws IOException, ClassNotFoundException {
        sliceInsertLock.lock();
        List<String> topicPathList = Arrays.stream(topicPath.split("->")).toList();
        memoryGraph.insertMemory(topicPathList, memorySlice);
        sliceInsertLock.unlock();
    }

    public void cleanMessage(List<Message> messages) {
        messageCleanLock.lock();
        memoryGraph.getChatMessages().removeAll(messages);
        messageCleanLock.unlock();
    }

    public void insertStaticMemory(String userId, Map<String, String> newStaticMemory) {
        memoryGraph.getStaticMemory().get(userId).putAll(newStaticMemory);
    }

    public void updateDialogMap(LocalDateTime dateTime,String newDialogCache) {
        memoryGraph.updateDialogMap(dateTime, newDialogCache);
    }
}

package work.slhaf.agent.core.cognation.submodule.perceive;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.cognation.submodule.perceive.pojo.User;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class PerceiveCore extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;
    private static volatile PerceiveCore perceiveCore = new PerceiveCore();
    private static final ReentrantLock usersLock = new ReentrantLock();

    /**
     * 用户列表
     */
    private List<User> users = new ArrayList<>();

    public static PerceiveCore getInstance() {
        if (perceiveCore == null) {
            synchronized (PerceiveCore.class) {
                if (perceiveCore == null) {
                    perceiveCore = new PerceiveCore();
                }
            }
        }
        return perceiveCore;
    }

    public User selectUser(String userInfo, String platform) {
        User resultUser = null;
        usersLock.lock();
        for (User user : users) {
            HashMap<String, String> info = user.getInfo();
            if (info.containsKey(platform)) {
                if (info.get(platform).equals(userInfo)) {
                    resultUser = user;
                }
            }
        }
        usersLock.unlock();
        return resultUser;
    }

    public User addUser(String userInfo, String platform, String userNickName) {
        User user = new User();
        user.addInfo(platform, userInfo);
        user.setNickName(userNickName);
        user.setUuid(UUID.randomUUID().toString());

        usersLock.lock();
        users.add(user);
        usersLock.unlock();
        return user;
    }

    public User selectUser(String id) {
        usersLock.lock();
        for (User user : users) {
            if (user.getUuid().equals(id)) {
                return user;
            }
        }
        usersLock.unlock();
        return null;
    }

    public void updateUser(User temp) {
        usersLock.lock();
        User user = selectUser(temp.getUuid());
        user.setRelation(temp.getRelation());
        user.setImpressions(temp.getImpressions());
        user.setAttitude(temp.getAttitude());
        user.setStaticMemory(temp.getStaticMemory());
        usersLock.unlock();
    }
}

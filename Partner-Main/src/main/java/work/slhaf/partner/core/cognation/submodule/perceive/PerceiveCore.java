package work.slhaf.partner.core.cognation.submodule.perceive;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.core.cognation.cognation.exception.UserNotExistsException;
import work.slhaf.partner.core.cognation.submodule.perceive.pojo.User;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
@CapabilityCore(value = "perceive")
public class PerceiveCore extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;
    private static volatile PerceiveCore perceiveCore;
    private static final ReentrantLock usersLock = new ReentrantLock();

    /**
     * 用户列表
     */
    private List<User> users = new ArrayList<>();

    public PerceiveCore() {
        perceiveCore = this;
    }

    public static PerceiveCore getInstance() {
        return perceiveCore;
    }

    @CapabilityMethod
    public User getUser(String userInfo, String platform) {
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

    @CapabilityMethod
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

    @CapabilityMethod
    public User getUser(String id) {
        usersLock.lock();
        User resultUser = null;
        for (User user : users) {
            if (user.getUuid().equals(id)) {
                resultUser = user;
            }
        }
        usersLock.unlock();
        if (resultUser == null) {
            throw new UserNotExistsException("[PerceiveCore] 用户不存在: " + id);
        }
        return resultUser;
    }

    @CapabilityMethod
    public void updateUser(User temp) {
        usersLock.lock();
        User user = getUser(temp.getUuid());
        user.setRelation(temp.getRelation());
        user.setImpressions(temp.getImpressions());
        user.setAttitude(temp.getAttitude());
        user.setStaticMemory(temp.getStaticMemory());
        user.updateRelationChange(user.getRelationChange());
        usersLock.unlock();
    }
}

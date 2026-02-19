package work.slhaf.partner.core.perceive;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.cognation.exception.UserNotExistsException;
import work.slhaf.partner.core.perceive.pojo.User;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@CapabilityCore(value = "perceive")
@Getter
@Setter
public class PerceiveCore extends PartnerCore<PerceiveCore> {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final ReentrantLock usersLock = new ReentrantLock();

    /**
     * 用户列表
     */
    private List<User> users = new ArrayList<>();

    public PerceiveCore() throws IOException, ClassNotFoundException {
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

    @Override
    protected String getCoreKey() {
        return "perceive-core";
    }
}

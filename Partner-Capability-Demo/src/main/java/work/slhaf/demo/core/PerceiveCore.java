package work.slhaf.demo.core;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.demo.capability.interfaces.CapabilityCore;
import work.slhaf.demo.capability.interfaces.CapabilityMethod;

@CapabilityCore(value = "perceive")
@Slf4j
public class PerceiveCore {

    public static volatile PerceiveCore perceiveCore;

    public static PerceiveCore getInstance() {
        if (perceiveCore == null){
            synchronized (PerceiveCore.class){
                if (perceiveCore == null){
                    perceiveCore = new PerceiveCore();
                }
            }
        }
        return perceiveCore;
    }

    @CapabilityMethod
    public String getUser(String id){
        log.info("perceive: getUser");
        return "";
    }

    @CapabilityMethod
    public String addUser(String userInfo, String platform, String userNickName){
        log.info("perceive: addUser");
        return "";
    }

    @CapabilityMethod
    public void updateUser(String user){
        log.info("perceive: updateUser");
    }

}

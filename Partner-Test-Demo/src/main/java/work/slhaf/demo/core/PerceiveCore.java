package work.slhaf.demo.core;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.factory.capability.annotation.CapabilityMethod;

@CapabilityCore(value = "perceive")
@Slf4j
public class PerceiveCore {

    public static volatile PerceiveCore perceiveCore;

    private PerceiveCore() {
        perceiveCore = this;
    }

    public static PerceiveCore getInstance() {
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

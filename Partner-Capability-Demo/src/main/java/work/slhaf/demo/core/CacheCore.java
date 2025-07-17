package work.slhaf.demo.core;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.demo.capability.annotation.CapabilityCore;
import work.slhaf.demo.capability.annotation.CapabilityMethod;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@CapabilityCore(value = "cache")
@Slf4j
public class CacheCore {

    public static volatile CacheCore cacheCore;

    private CacheCore() {
        cacheCore = this;
    }

    public static CacheCore getInstance() {
        return cacheCore;
    }

    @CapabilityMethod
    public HashMap<LocalDateTime, String> getDialogMap() {
        log.info("cache: getDialogMap");
        return new HashMap<>();
    }

    @CapabilityMethod
    public ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId) {
        log.info("cache: getUserDialogMap");
        return new ConcurrentHashMap<>();
    }

    @CapabilityMethod
    public void updateDialogMap(LocalDateTime dateTime, String newDialogCache) {
        log.info("cache: updateDialogMap");
    }

    @CapabilityMethod
    public String getDialogMapStr() {
        log.info("cache: getDialogMapStr");
        return "";
    }

    @CapabilityMethod
    public String getUserDialogMapStr(String userId,int id) {
        log.info("cache: getUserDialogMapStr");
        return userId+id;
    }

}

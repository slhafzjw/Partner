package work.slhaf.demo.core;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.demo.capability.interfaces.CapabilityCore;
import work.slhaf.demo.capability.interfaces.CapabilityMethod;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@CapabilityCore(value = "cache")
@Slf4j
public class CacheCore {

    public static volatile CacheCore cacheCore;

    public static CacheCore getInstance() {
        if (cacheCore == null) {
            synchronized (CacheCore.class) {
                if (cacheCore == null) {
                    cacheCore = new CacheCore();
                }
            }
        }
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
    public String getUserDialogMapStr(String userId) {
        log.info("cache: getUserDialogMapStr");
        return "";
    }

}

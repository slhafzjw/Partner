package work.slhaf.demo.ability;


import work.slhaf.demo.capability.annotation.Capability;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Capability(value = "cache")
public interface CacheCapability {
    HashMap<LocalDateTime, String> getDialogMap();
    ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId);
    void updateDialogMap(LocalDateTime dateTime, String newDialogCache);
    String getDialogMapStr();
    String getUserDialogMapStr(String userId,int id);
}

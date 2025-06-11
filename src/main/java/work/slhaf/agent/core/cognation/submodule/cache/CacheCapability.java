package work.slhaf.agent.core.cognation.submodule.cache;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public interface CacheCapability {
    HashMap<LocalDateTime, String> getDialogMap();
    ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId);
    void updateDialogMap(LocalDateTime dateTime, String newDialogCache);
    String getDialogMapStr();
    String getUserDialogMapStr(String userId);
}

package work.slhaf.demo.core;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.demo.capability.interfaces.CapabilityCore;
import work.slhaf.demo.capability.interfaces.CapabilityMethod;

import java.util.ArrayList;
import java.util.List;

@CapabilityCore(value = "memory")
@Slf4j
public class MemoryCore {

    public static volatile MemoryCore memoryCore;

    public static MemoryCore getInstance() {
        if (memoryCore == null){
            synchronized (MemoryCore.class){
                if (memoryCore == null){
                    memoryCore = new MemoryCore();
                }
            }
        }
        return memoryCore;
    }

    @CapabilityMethod
    public void cleanSelectedSliceFilter(){
        log.info("memory: cleanSelectedSliceFilter");
    }

    @CapabilityMethod
    public String getTopicTree(){
        log.info("memory: getTopicTree");
        return "";
    }

    @CapabilityMethod
    public List<String> listMemory(String userId){
        log.info("memory: listMemory");
        return new ArrayList<>();
    }
}

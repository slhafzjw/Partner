package work.slhaf.demo.core;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.capability.annotation.CapabilityMethod;

import java.util.ArrayList;
import java.util.List;

@CapabilityCore(value = "memory")
@Slf4j
public class MemoryCore {

    public static volatile MemoryCore memoryCore;

    private MemoryCore() {
        memoryCore = this;
    }

    public static MemoryCore getInstance() {
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

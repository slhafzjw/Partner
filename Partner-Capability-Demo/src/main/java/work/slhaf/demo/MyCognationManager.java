package work.slhaf.demo;

import work.slhaf.demo.capability.BaseCognationManager;
import work.slhaf.demo.capability.annotation.Coordinated;

import java.util.List;

public class MyCognationManager extends BaseCognationManager {

    @Coordinated(capability = "memory")
    public List<String> selectMemory(String path) {
        return List.of("1", "2", path);
    }
}

package work.slhaf.demo;


import work.slhaf.partner.api.factory.capability.annotation.CoordinateManager;
import work.slhaf.partner.api.factory.capability.annotation.Coordinated;

import java.util.List;

@CoordinateManager
public class MyCognationManager {

    @Coordinated(capability = "memory")
    public List<String> selectMemory(String path) {
        return List.of("1", "2", path);
    }
}

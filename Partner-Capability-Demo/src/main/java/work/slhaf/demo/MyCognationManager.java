package work.slhaf.demo;

import work.slhaf.demo.capability.BaseCognationManager;
import work.slhaf.demo.capability.interfaces.Coordinated;

import java.util.ArrayList;
import java.util.List;

public class MyCognationManager extends BaseCognationManager {

    @Coordinated(capability = "memory")
    public List<String> selectMemory(String path){
        return null;
    }
}

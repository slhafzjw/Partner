import org.junit.jupiter.api.Test;
import work.slhaf.demo.ability.CacheCapability;
import work.slhaf.demo.capability.annotation.InjectCapability;

public class FunctionRouterTest {

    @InjectCapability
    private CacheCapability cache;

    @Test
    public void test(){
        cache.getUserDialogMapStr("123",111);
    }

}

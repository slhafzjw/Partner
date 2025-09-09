import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.common.pojo.MemoryResult;
import work.slhaf.partner.core.submodule.memory.MemoryCapability;

import java.lang.reflect.Proxy;
import java.util.function.Function;

public class ReflectionTest {

    @Test
    public void test1() {
    }

    @Test
    public void proxyTest() {
        MemoryCapability memory = (MemoryCapability) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{MemoryCapability.class}, (proxy, method, args) -> {
            if ("selectMemory".equals(method.getName())){
                System.out.println(111);
                return new MemoryResult();
            }
            return null;
        });
        memory.selectMemory("111");

        Function<String, Integer> function = s -> {
            return s.length();
        };
    }
}

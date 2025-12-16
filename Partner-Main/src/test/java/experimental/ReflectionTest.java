package experimental;

import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemoryResult;

import java.lang.reflect.Proxy;

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
    }
}

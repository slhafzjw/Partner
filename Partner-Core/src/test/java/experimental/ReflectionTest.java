package experimental;

import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.memory.MemoryCapability;

import java.lang.reflect.Proxy;

public class ReflectionTest {

    @Test
    public void test1() {
    }

    @Test
    public void proxyTest() {
        MemoryCapability memory = (MemoryCapability) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{MemoryCapability.class}, (proxy, method, args) -> {
            if ("getCurrentMemoryId".equals(method.getName())) {
                System.out.println(111);
                return "memory-id";
            }
            return null;
        });
        memory.getCurrentMemoryId();
    }
}

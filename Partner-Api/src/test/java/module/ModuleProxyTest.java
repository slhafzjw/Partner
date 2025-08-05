package module;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.api.flow.abstracts.AgentInteractionModule;

import java.lang.reflect.InvocationTargetException;

public class ModuleProxyTest {
    @Test
    public void test() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<? extends AgentInteractionModule> clazz = new ByteBuddy().subclass(MyAgentInteractionModule.class)
                .method(ElementMatchers.isOverriddenFrom(AgentInteractionModule.class))
                .intercept(MethodDelegation.to(
                        new MyModuleProxyInterceptor()
                ))
                .make()
                .load(ModuleProxyTest.class.getClassLoader())
                .getLoaded();
        clazz.getConstructor().newInstance().execute(null);
    }
}

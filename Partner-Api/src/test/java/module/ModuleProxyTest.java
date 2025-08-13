package module;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;

import java.lang.reflect.InvocationTargetException;

public class ModuleProxyTest {
    @Test
    public void test() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<? extends AgentRunningModule> clazz = new ByteBuddy().subclass(MyAgentRunningModule.class)
                .method(ElementMatchers.isOverriddenFrom(AgentRunningModule.class))
                .intercept(MethodDelegation.to(
                        new MyModuleProxyInterceptor()
                ))
                .make()
                .load(ModuleProxyTest.class.getClassLoader())
                .getLoaded();
        clazz.getConstructor().newInstance().execute(null);
    }
}

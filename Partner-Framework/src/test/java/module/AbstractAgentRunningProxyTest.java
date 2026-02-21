package module;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class AbstractAgentRunningProxyTest {
    @Test
    public void test() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, IOException, ClassNotFoundException {
        Class<? extends AbstractAgentRunningModule> clazz = new ByteBuddy().subclass(MyAbstractAgentRunningAbstractAgentModule.class)
                .method(ElementMatchers.isOverriddenFrom(AbstractAgentRunningModule.class))
                .intercept(MethodDelegation.to(
                        new MyModuleProxyInterceptor()
                ))
                .make()
                .load(AbstractAgentRunningProxyTest.class.getClassLoader())
                .getLoaded();
        clazz.getConstructor().newInstance().execute(null);
    }
}

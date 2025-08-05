package module;

import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class MyModuleProxyInterceptor {
    public MyModuleProxyInterceptor() {}

    @RuntimeType
    public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> zuper, @This Object proxy) throws Exception {
        System.out.println("22222");
        Object res = zuper.call();
        System.out.println("11111");
        return res;
    }
}

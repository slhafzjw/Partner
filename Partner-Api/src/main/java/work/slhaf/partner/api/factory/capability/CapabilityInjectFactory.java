package work.slhaf.partner.api.factory.capability;

import org.reflections.Reflections;
import work.slhaf.partner.api.factory.capability.annotation.Capability;
import work.slhaf.partner.api.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.factory.capability.annotation.ToCoordinated;
import work.slhaf.partner.api.factory.capability.exception.ProxySetFailedExceptionCapability;
import work.slhaf.partner.api.factory.entity.AgentBaseFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

import static work.slhaf.partner.api.common.util.AgentUtil.methodSignature;

/**
 * 负责执行<code>Capability</code>的注入逻辑
 */
public class CapabilityInjectFactory extends AgentBaseFactory {

    private Reflections reflections;
    private HashMap<String, Function<Object[], Object>> coordinatedMethodsRouterTable;
    private HashMap<String, Function<Object[], Object>> methodsRouterTable;
    private HashMap<Class<?>, Object> capabilityHolderInstances;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        reflections = context.getReflections();
        coordinatedMethodsRouterTable = context.getCoordinatedMethodsRouterTable();
        methodsRouterTable = context.getMethodsRouterTable();
        capabilityHolderInstances = context.getCapabilityHolderInstances();
    }

    @Override
    protected void run() {
        //获取现有的`@InjectCapability`注解所在字段，并获取对应的类，通过动态代理注入对象
        Set<Field> fields = reflections.getFieldsAnnotatedWith(InjectCapability.class);
        //在动态代理内部，通过函数路由表调用对应的方法
        createProxy(fields);
    }

    private void createProxy(Set<Field> fields) {
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
                Object instance = Proxy.newProxyInstance(
                        fieldType.getClassLoader(),
                        new Class[]{fieldType},
                        (proxy, method, objects) -> {
                            if (method.isAnnotationPresent(ToCoordinated.class)) {
                                String key = method.getDeclaringClass().getAnnotation(Capability.class).value() + "." + methodSignature(method);
                                return coordinatedMethodsRouterTable.get(key).apply(objects);
                            }
                            String key = fieldType.getAnnotation(Capability.class).value() + "." + methodSignature(method);
                            return methodsRouterTable.get(key).apply(objects);
                        }
                );
                field.set(capabilityHolderInstances.get(field.getDeclaringClass()), instance);
            }
        } catch (Exception e) {
            throw new ProxySetFailedExceptionCapability("代理设置失败", e);
        }
    }

}

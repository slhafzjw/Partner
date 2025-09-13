package work.slhaf.partner.api.agent.factory.capability;

import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.capability.annotation.ToCoordinated;
import work.slhaf.partner.api.agent.factory.capability.exception.ProxySetFailedExceptionCapability;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.CapabilityFactoryContext;
import work.slhaf.partner.api.agent.factory.module.ModuleInitHookExecuteFactory;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

import static work.slhaf.partner.api.agent.util.AgentUtil.methodSignature;

/**
 * <h2>Agent启动流程 6</h2>
 *
 * <p>负责执行 {@link Capability} 的注入逻辑。</p>
 *
 * <p>实现方式：</p>
 * <ol>
 *     <li>通过动态代理，为 {@link AgentModule} 与 {@link AgentSubModule} 中待注入的
 *         <b>能力接口</b> 类型（即 {@link Capability} 标注的接口类）生成代理对象。
 *     </li>
 *     <li>在代理对象内部，根据调用方法的签名确定路由，将调用转发至对应的具体函数。
 *     </li>
 *     <li>通过此机制，实现了 {@link Capability} 单一语义层面上普通方法与协调方法的统一入口。
 *     </li>
 * </ol>
 *
 * <p>下一步流程请参阅 {@link ModuleInitHookExecuteFactory}</p>
 */public class CapabilityInjectFactory extends AgentBaseFactory {

    private Reflections reflections;
    private HashMap<String, Function<Object[], Object>> coordinatedMethodsRouterTable;
    private HashMap<String, Function<Object[], Object>> methodsRouterTable;
    private HashMap<Class<?>, Object> capabilityHolderInstances;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        CapabilityFactoryContext factoryContext = context.getCapabilityFactoryContext();
        reflections = context.getReflections();
        coordinatedMethodsRouterTable = factoryContext.getCoordinatedMethodsRouterTable();
        methodsRouterTable = factoryContext.getMethodsRouterTable();
        capabilityHolderInstances = factoryContext.getCapabilityHolderInstances();
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

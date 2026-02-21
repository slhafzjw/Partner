package work.slhaf.partner.api.agent.factory.capability;

import cn.hutool.core.util.ClassUtil;
import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.capability.annotation.*;
import work.slhaf.partner.api.agent.factory.capability.exception.CapabilityCoreInstancesCreateFailedException;
import work.slhaf.partner.api.agent.factory.capability.exception.CapabilityFactoryExecuteFailedException;
import work.slhaf.partner.api.agent.factory.capability.exception.DuplicateMethodException;
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.CapabilityFactoryContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static work.slhaf.partner.api.agent.util.AgentUtil.methodSignature;


/**
 * <h2>Agent启动流程 5</h2>
 *
 * <p>
 * 负责收集注解 {@link Capability} 和 {@link CapabilityCore} 标识的类，并生成函数路由表、创建core、capability实例，以及放入instanceMap供后续进行注入操作
 * </p>
 *
 * <ol>
 *     <li>
 *         <p>{@link CapabilityRegisterFactory#setCoreInstances()}</p>
 *         通过反射调用无参构造函数创建core实例，并将实例放入instanceMap供后续使用
 *     </li>
 *     <li>
 *         <p>{@link CapabilityRegisterFactory#generateRouterTable()}</p>
 *         生成函数路由表:
 *         <ul>
 *             <li>
 *                 <p>{@link CapabilityRegisterFactory#generateMethodsRouterTable()}</p>
 *                 生成普通方法对应的函数路由表
 *             </li>
 *             <li>
 *                 <p>{@link CapabilityRegisterFactory#generateCoordinatedMethodsRouterTable()}</p>
 *                 生成协调方法对应的函数路由表
 *             </li>
 *         </ul>
 *     </li>
 *     <li>
 *         函数路由表生成完毕、core实例创建完毕之后，将交由下一工厂完成能力(Capability)注入操作，注入到 {@link AgentRunningModule} 与 {@link AgentSubModule} 对应的实例中
 *     </li>
 * </ol>
 *
 * <p>下一步流程请参阅{@link CapabilityInjectFactory}</p>
 */
public class CapabilityRegisterFactory extends AgentBaseFactory {

    private Reflections reflections;
    private HashMap<String, Function<Object[], Object>> methodsRouterTable;
    private HashMap<String, Function<Object[], Object>> coordinatedMethodsRouterTable;
    private HashMap<Class<?>, Object> coreInstances;
    private HashMap<Class<?>, Object> capabilityHolderInstances;
    private Set<Class<?>> cores;
    private Set<Class<?>> capabilities;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        CapabilityFactoryContext factoryContext = context.getCapabilityFactoryContext();
        reflections = context.getReflections();
        methodsRouterTable = factoryContext.getMethodsRouterTable();
        coordinatedMethodsRouterTable = factoryContext.getCoordinatedMethodsRouterTable();
        coreInstances = factoryContext.getCapabilityCoreInstances();
        cores = factoryContext.getCores();
        capabilities = factoryContext.getCapabilities();
        capabilityHolderInstances = factoryContext.getCapabilityHolderInstances();
    }

    @Override
    protected void run() {
        setCapabilityHolderInstances();
        setCoreInstances();
        generateRouterTable();
    }

    private void setCapabilityHolderInstances() {
        Set<Class<?>> collect = reflections.getTypesAnnotatedWith(AgentComponent.class).stream()
                .filter(ClassUtil::isNormalClass)
                .filter(clazz -> !capabilityHolderInstances.containsKey(clazz))
                .collect(Collectors.toSet());
        for (Class<?> clazz : collect) {
            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                if (constructor.canAccess(null)) {
                    throw new CapabilityFactoryExecuteFailedException("缺少无参构造方法的类: " + clazz);
                }
                Object o = constructor.newInstance();
                capabilityHolderInstances.put(clazz, o);
            } catch (Exception e) {
                throw new CapabilityFactoryExecuteFailedException("创建代理对象失败: " + clazz, e);
            }
        }
    }

    /**
     * 生成函数路由表
     */
    private void generateRouterTable() {
        generateMethodsRouterTable();
        generateCoordinatedMethodsRouterTable();
    }

    /**
     * 生成协调函数对应的函数路由表
     */
    private void generateCoordinatedMethodsRouterTable() {
        Set<Method> methodsAnnotatedWith = reflections.getMethodsAnnotatedWith(Coordinated.class);
        if (methodsAnnotatedWith.isEmpty()) {
            return;
        }
        try {
            //获取所有CM实例
            HashMap<String, Object> coordinateManagerInstances = getCoordinateManagerInstances();
            methodsAnnotatedWith.forEach(method -> {
                String key = method.getAnnotation(Coordinated.class).capability() + "." + methodSignature(method);
                Function<Object[], Object> function = args -> {
                    try {
                        return method.invoke(coordinateManagerInstances.get(key), args);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                };
                coordinatedMethodsRouterTable.put(key, function);
            });
        } catch (Exception e) {
            throw new CapabilityFactoryExecuteFailedException("创建协调方法路由表出错", e);
        }

    }

    /**
     * 获取<code>CoordinateManager</code>子类实例
     */
    private HashMap<String, Object> getCoordinateManagerInstances() throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        HashMap<String, Object> map = new HashMap<>();
        for (Class<?> c : reflections.getTypesAnnotatedWith(CoordinateManager.class)) {
            Constructor<?> constructor = c.getDeclaredConstructor();
            Object instance = constructor.newInstance();
            setCores(instance, c);
            Arrays.stream(c.getMethods())
                    .filter(method -> method.isAnnotationPresent(Coordinated.class))
                    .forEach(method -> {
                        String key = method.getAnnotation(Coordinated.class).capability() + "." + methodSignature(method);
                        map.put(key, instance);
                    });
        }
        return map;
    }

    private void setCores(Object cmInstance, Class<?> cmClazz) throws IllegalAccessException {
        for (Field field : cmClazz.getFields()) {
            if (field.getType().isAnnotationPresent(CapabilityCore.class)) {
                field.setAccessible(true);
                field.set(cmInstance, coreInstances.get(field.getType()));
            }
        }
    }

    /**
     * 扫描`@Capability`与`@CapabilityMethod`注解的类与方法
     * 将`capabilityValue.methodSignature`作为key,函数对象为通过反射拿到的core实例对应的方法
     */
    private void generateMethodsRouterTable() {
        cores.forEach(core -> Arrays.stream(core.getMethods())
                .filter(method -> method.isAnnotationPresent(CapabilityMethod.class))
                .forEach(method -> {
                    Function<Object[], Object> function = args -> {
                        try {
                            return method.invoke(coreInstances.get(core), args);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    };
                    String key = core.getAnnotation(CapabilityCore.class).value() + "." + methodSignature(method);
                    if (methodsRouterTable.containsKey(key)) {
                        throw new DuplicateMethodException("重复注册能力方法: " + core.getPackage().getName() + "." + core.getSimpleName() + "#" + method.getName());
                    }
                    methodsRouterTable.put(key, function);
                }));
    }

    /**
     * 反射获取<code>CapabilityCore</code>实例
     */
    private void setCoreInstances() {
        try {
            for (Class<?> core : cores) {
                Constructor<?> constructor = core.getDeclaredConstructor();
                constructor.setAccessible(true);
                coreInstances.put(core, constructor.newInstance());
            }
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException |
                 IllegalAccessException e) {
            throw new CapabilityCoreInstancesCreateFailedException("core实例创建失败");
        }
    }
}

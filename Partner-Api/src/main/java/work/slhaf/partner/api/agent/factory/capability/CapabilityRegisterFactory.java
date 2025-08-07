package work.slhaf.partner.api.agent.factory.capability;

import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.capability.annotation.*;
import work.slhaf.partner.api.agent.factory.capability.exception.CapabilityFactoryExecuteFailedException;
import work.slhaf.partner.api.agent.factory.capability.exception.CoreInstancesCreateFailedExceptionCapability;
import work.slhaf.partner.api.agent.factory.capability.exception.DuplicateMethodException;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.CapabilityFactoryContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

import static cn.hutool.core.util.ClassUtil.isNormalClass;
import static work.slhaf.partner.api.agent.util.AgentUtil.methodSignature;


/**
 * 负责获取<code>@Capability</code>和<code>@CapabilityCore</code>标识的类，并生成函数路由表、设置<code>Core</code>实例用于后续注入
 */
public final class CapabilityRegisterFactory extends AgentBaseFactory {

    private Reflections reflections;
    private HashMap<String, Function<Object[], Object>> methodsRouterTable;
    private HashMap<String, Function<Object[], Object>> coordinatedMethodsRouterTable;
    private HashMap<Class<?>, Object> capabilityCoreInstances;
    private HashMap<Class<?>, Object> capabilityHolderInstances;
    private Set<Class<?>> cores;
    private Set<Class<?>> capabilities;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        CapabilityFactoryContext factoryContext = context.getCapabilityFactoryContext();
        reflections = context.getReflections();
        methodsRouterTable = factoryContext.getMethodsRouterTable();
        coordinatedMethodsRouterTable = factoryContext.getCoordinatedMethodsRouterTable();
        capabilityCoreInstances = factoryContext.getCapabilityCoreInstances();
        cores = factoryContext.getCores();
        capabilities = factoryContext.getCapabilities();
        capabilityHolderInstances = factoryContext.getCapabilityHolderInstances();
    }

    @Override
    protected void run() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        setCapabilityCoreInstances();
        setAnnotatedClasses();
        generateRouterTable();
    }

    /**
     * 设置<code>CapabilityCore</code>、<code>Capability</code>注解标识类
     */
    private void setAnnotatedClasses() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        cores.addAll(reflections.getTypesAnnotatedWith(CapabilityCore.class));
        capabilities.addAll(reflections.getTypesAnnotatedWith(Capability.class));
        setCapabilityHolderInstances();
    }

    private void setCapabilityHolderInstances() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> clazz : reflections.getTypesAnnotatedWith(CapabilityHolder.class)) {
            if (!isNormalClass(clazz)){
                continue;
            }
            Object o = clazz.getDeclaredConstructor().newInstance();
            capabilityHolderInstances.put(clazz, o);
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

            Arrays.stream(c.getMethods())
                    .filter(method -> method.isAnnotationPresent(Coordinated.class))
                    .forEach(method -> {
                        String key = method.getAnnotation(Coordinated.class).capability() + "." + methodSignature(method);
                        map.put(key, instance);
                    });
        }
        return map;
    }

    /**
     * 生成普通方法对应的函数路由表
     */
    private void generateMethodsRouterTable() {
        //扫描`@Capability`与`@CapabilityMethod`注解的类与方法
        //将`capabilityValue.methodSignature`作为key,函数对象为通过反射拿到的core实例对应的方法
        cores.forEach(core -> Arrays.stream(core.getMethods())
                .filter(method -> method.isAnnotationPresent(CapabilityMethod.class))
                .forEach(method -> {
                    Function<Object[], Object> function = args -> {
                        try {
                            return method.invoke(capabilityCoreInstances.get(core), args);
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
    private void setCapabilityCoreInstances() {
        try {
            for (Class<?> core : cores) {
                Constructor<?> constructor = core.getDeclaredConstructor();
                constructor.setAccessible(true);
                capabilityCoreInstances.put(core, constructor.newInstance());
            }
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException |
                 IllegalAccessException e) {
            throw new CoreInstancesCreateFailedExceptionCapability("core实例创建失败");
        }
    }
}

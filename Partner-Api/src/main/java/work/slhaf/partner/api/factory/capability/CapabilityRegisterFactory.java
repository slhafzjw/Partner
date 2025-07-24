package work.slhaf.partner.api.factory.capability;

import org.reflections.Reflections;
import work.slhaf.partner.api.factory.entity.AgentBaseFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;
import work.slhaf.partner.api.factory.capability.annotation.*;
import work.slhaf.partner.api.factory.capability.exception.CoreInstancesCreateFailedException;
import work.slhaf.partner.api.factory.capability.exception.DuplicateMethodException;
import work.slhaf.partner.api.factory.capability.exception.FactoryExecuteFailedException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

import static work.slhaf.partner.api.common.util.AgentUtil.methodSignature;


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
        reflections = context.getReflections();
        methodsRouterTable = context.getMethodsRouterTable();
        coordinatedMethodsRouterTable = context.getCoordinatedMethodsRouterTable();
        capabilityCoreInstances = context.getCapabilityCoreInstances();
        capabilityHolderInstances = context.getCapabilityHolderInstances();
        cores = context.getCores();
        capabilities = context.getCapabilities();
    }

    @Override
    protected void run() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        setCapabilityCoreInstances();
        setAnnotatedClasses();
        generateRouterTable();
    }

    private void setAnnotatedClasses() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        cores.addAll(reflections.getTypesAnnotatedWith(CapabilityCore.class));
        capabilities.addAll(reflections.getTypesAnnotatedWith(Capability.class));
        setCapabilityHolderInstances();
    }

    private void setCapabilityHolderInstances() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> clazz : reflections.getTypesAnnotatedWith(CapabilityHolder.class)) {
            Object o = clazz.getDeclaredConstructor().newInstance();
            capabilityHolderInstances.put(clazz, o);
        }
    }

    private void generateRouterTable() {
        generateMethodsRouterTable();
        generateCoordinatedMethodsRouterTable();
    }

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
            throw new FactoryExecuteFailedException("创建协调方法路由表出错", e);
        }

    }

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

    private void setCapabilityCoreInstances() {
        try {
            for (Class<?> core : cores) {
                Constructor<?> constructor = core.getDeclaredConstructor();
                constructor.setAccessible(true);
                capabilityCoreInstances.put(core, constructor.newInstance());
            }
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException |
                 IllegalAccessException e) {
            throw new CoreInstancesCreateFailedException("core实例创建失败");
        }
    }
}

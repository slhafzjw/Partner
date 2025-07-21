package work.slhaf.partner.api.capability;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import work.slhaf.partner.api.capability.annotation.*;
import work.slhaf.partner.api.capability.exception.*;
import work.slhaf.partner.api.capability.module.CapabilityHolder;
import work.slhaf.partner.api.capability.util.CapabilityUtil;

import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static work.slhaf.partner.api.capability.util.CapabilityUtil.methodSignature;


public final class CapabilityRegisterFactory {

    public static volatile CapabilityRegisterFactory capabilityRegisterFactory;

    private Reflections reflections;
    private final HashMap<String, Function<Object[], Object>> methodsRouterTable = new HashMap<>();
    private final HashMap<String, Function<Object[], Object>> coordinatedMethodsRouterTable = new HashMap<>();
    private final HashMap<Class<?>, Object> capabilityCoreInstances = new HashMap<>();
    private final HashMap<Class<?>, Object> capabilityHolderInstances = new HashMap<>();
    private Set<Class<?>> cores;
    private Set<Class<?>> capabilities;

    private CapabilityRegisterFactory() {
    }

    public static CapabilityRegisterFactory getInstance() {
        if (capabilityRegisterFactory == null) {
            synchronized (CapabilityRegisterFactory.class) {
                if (capabilityRegisterFactory == null) {
                    capabilityRegisterFactory = new CapabilityRegisterFactory();
                }
            }
        }
        return capabilityRegisterFactory;
    }


    public void registerCapabilities(String scannerPath) {
        setBasicVariable(scannerPath);
        //检查可注册能力是否正常
        statusCheck();
        generateMethodsRouterTable();
        generateCoordinatedMethodsRouterTable();
        //扫描现有Capability, value为键，返回函数路由表, 函数路由表内部通过反射调用对应core的方法
        injectCapability();
    }

    private void generateCoordinatedMethodsRouterTable() {
        Set<Method> methodsAnnotatedWith = reflections.getMethodsAnnotatedWith(Coordinated.class);
        if (methodsAnnotatedWith.isEmpty()) {
            return;
        }
        try {
            //获取所有CM实例
            HashMap<String, Object> cognationManagerInstances = getCognationManagerInstances();
            methodsAnnotatedWith.forEach(method -> {
                String key = method.getAnnotation(Coordinated.class).capability() + "." + methodSignature(method);
                Function<Object[], Object> function = args -> {
                    try {
                        return method.invoke(cognationManagerInstances.get(key), args);
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

    private HashMap<String, Object> getCognationManagerInstances() throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        HashMap<String, Object> map = new HashMap<>();
        for (Class<? extends BaseCoordinateManager> c : reflections.getSubTypesOf(BaseCoordinateManager.class)) {
            Constructor<? extends BaseCoordinateManager> constructor = c.getDeclaredConstructor();
            BaseCoordinateManager instance = constructor.newInstance();

            Arrays.stream(c.getMethods())
                    .filter(method -> method.isAnnotationPresent(Coordinated.class))
                    .forEach(method -> {
                        String key = method.getAnnotation(Coordinated.class).capability() + "." + methodSignature(method);
                        map.put(key, instance);
                    });
        }
        return map;
    }

    private void setBasicVariable(String scannerPath) {
        setReflections(scannerPath);
        setAnnotatedClasses();
    }

    private void setAnnotatedClasses() {
        cores = reflections.getTypesAnnotatedWith(CapabilityCore.class);
        capabilities = reflections.getTypesAnnotatedWith(Capability.class);
    }

    private void setReflections(String scannerPath) {
        //后续可替换为根据传入的启动类获取路径
        Collection<URL> urls = ClasspathHelper.forPackage(scannerPath);
        reflections = new Reflections(
                new ConfigurationBuilder()
                        .setUrls(urls)
                        .setScanners(
                                Scanners.TypesAnnotated,
                                Scanners.MethodsAnnotated,
                                Scanners.SubTypes,
                                Scanners.FieldsAnnotated
                        )
        );
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


    private void injectCapability() {
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
            throw new ProxySetFailedException("代理设置失败", e);
        }
    }

    private void statusCheck() {
        capabilityHolderCheck();
        checkCountAndCapabilities();
        checkCapabilityMethods();
        checkCoordinatedMethods();
        checkInjectCapability();
        //检查完毕，设置core的实例类
        setCapabilityCoreInstances();
    }

    private void checkInjectCapability() {
        reflections.getFieldsAnnotatedWith(InjectCapability.class).forEach(field -> {
            if (!CapabilityHolder.class.isAssignableFrom(field.getDeclaringClass())) {
                throw new UnMatchedCapabilityException("InjectCapability 注解只能用于CapabilityHolder的子类");
            }
        });
    }

    private void capabilityHolderCheck() {
        if (capabilityHolderInstances.isEmpty()) {
            throw new EmptyCapabilityHolderException("Capability 持有者实例为空");
        }
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

    private void checkCoordinatedMethods() {
        //检查各个capability中是否含有ToCoordinated注解
        //如果含有，则需要查找AbstractCognationManager的子类,看这里是否有对应的Coordinated注解所在方法
        Set<String> methodsToCoordinated = capabilities.stream()
                .flatMap(capability -> Arrays.stream(capability.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(ToCoordinated.class))
                .map(method -> {
                    String capabilityValue = method.getDeclaringClass().getAnnotation(Capability.class).value();
                    return capabilityValue + "." + methodSignature(method);
                })
                .collect(Collectors.toSet());
        if (!methodsToCoordinated.isEmpty()) {
            Set<Class<? extends BaseCoordinateManager>> subTypesOfAbsCM = reflections.getSubTypesOf(BaseCoordinateManager.class);
            Set<String> methodsCoordinated = getMethodsCoordinated(subTypesOfAbsCM);
            if (!methodsCoordinated.equals(methodsToCoordinated)) {
                // 找出缺少的协调方法
                Set<String> missingMethods = new HashSet<>(methodsToCoordinated);
                missingMethods.removeAll(methodsCoordinated);

                // 找出多余的协调方法
                Set<String> extraMethods = new HashSet<>(methodsCoordinated);
                extraMethods.removeAll(methodsToCoordinated);

                // 抛出异常或记录错误
                if (!missingMethods.isEmpty()) {
                    throw new UnMatchedCoordinatedMethodException("缺少协调方法: " + String.join(", ", missingMethods));
                }
                if (!extraMethods.isEmpty()) {
                    throw new UnMatchedCoordinatedMethodException("发现多余的协调方法: " + String.join(", ", extraMethods));
                }
            }
        }
    }

    private Set<String> getMethodsCoordinated(Set<Class<? extends BaseCoordinateManager>> subTypesOfAbsCM) {
        Set<String> methodsCoordinated = new HashSet<>();
        for (Class<? extends BaseCoordinateManager> cm : subTypesOfAbsCM) {
            Method[] methods = cm.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Coordinated.class)) {
                    methodsCoordinated.add(method.getAnnotation(Coordinated.class).capability() + "." + methodSignature(method));
                }
            }
        }
        return methodsCoordinated;
    }


    private void checkCapabilityMethods() {
        HashMap<String, List<Method>> capabilitiesMethods = getCapabilityMethods(capabilities);
        StringBuilder sb = new StringBuilder();
        for (Class<?> core : cores) {
            List<Method> methodsWithAnnotation = Arrays.stream(core.getMethods())
                    .filter(method -> method.isAnnotationPresent(CapabilityMethod.class))
                    .toList();
            List<Method> capabilityMethods = capabilitiesMethods.get(core.getAnnotation(CapabilityCore.class).value());
            LackRecord lackRecord = checkMethodsMatched(methodsWithAnnotation, capabilityMethods);
            if (lackRecord.hasNotEmptyRecord()) {
                sb.append(lackRecord.toLackErrorMsg(core.getAnnotation(CapabilityCore.class).value()));
            }
        }
        if (!sb.isEmpty()) {
            throw new UnMatchedCapabilityMethodException(sb.toString());
        }
    }

    private LackRecord checkMethodsMatched(List<Method> methodsWithAnnotation, List<Method> capabilityMethods) {
        Set<String> collectedMethodsWithAnnotation = methodsWithAnnotation.stream()
                .filter(method -> !method.isAnnotationPresent(ToCoordinated.class))
                .map(CapabilityUtil::methodSignature)
                .collect(Collectors.toSet());
        Set<String> collectedCapabilityMethods = capabilityMethods.stream()
                .filter(method -> !method.isAnnotationPresent(ToCoordinated.class))
                .map(CapabilityUtil::methodSignature)
                .collect(Collectors.toSet());
        return checkMethodsMatched(collectedMethodsWithAnnotation, collectedCapabilityMethods);
    }

    private LackRecord checkMethodsMatched(Set<String> collectedMethodsWithAnnotation, Set<String> collectedCapabilityMethods) {
        List<String> coreLack = new ArrayList<>();
        List<String> capLack = new ArrayList<>();
        // 找出 core 中多余的方法
        for (String coreSig : collectedMethodsWithAnnotation) {
            if (!collectedCapabilityMethods.contains(coreSig)) {
                capLack.add(coreSig);
            }
        }
        // 找出 capability 中多余的方法
        for (String capSig : collectedCapabilityMethods) {
            if (!collectedMethodsWithAnnotation.contains(capSig)) {
                coreLack.add(capSig);
            }
        }
        return new LackRecord(coreLack, capLack);

    }


    private HashMap<String, List<Method>> getCapabilityMethods(Set<Class<?>> capabilities) {
        HashMap<String, List<Method>> capabilityMethods = new HashMap<>();
        capabilities.forEach(capability -> {
            capabilityMethods.put(capability.getAnnotation(Capability.class).value(), Arrays.stream(capability.getMethods()).toList());
        });
        return capabilityMethods;
    }

    private void checkCountAndCapabilities() {
        if (cores.size() != capabilities.size()) {
            throw new UnMatchedCapabilityException("Capability 注册异常: 已存在的CapabilityCore与Capability数量不匹配!");
        }
        if (!checkValuesMatched(cores, capabilities)) {
            throw new UnMatchedCapabilityException("Capability 注册异常: 已存在的CapabilityCore与Capability不匹配!");
        }
    }


    private boolean checkValuesMatched(Set<Class<?>> cores, Set<Class<?>> capabilities) {
        Set<String> coresValues = new HashSet<>();
        Set<String> capabilitiesValues = new HashSet<>();
        for (Class<?> core : cores) {
            CapabilityCore annotation = core.getAnnotation(CapabilityCore.class);
            if (annotation != null) {
                if (coresValues.contains(annotation.value())) {
                    throw new DuplicateCapabilityException(String.format("Capability 注册异常: 重复的Capability核心: %s", annotation.value()));
                }
                coresValues.add(annotation.value());
            }
        }
        for (Class<?> capability : capabilities) {
            Capability annotation = capability.getAnnotation(Capability.class);
            if (annotation != null) {
                if (capabilitiesValues.contains(annotation.value())) {
                    throw new DuplicateCapabilityException(String.format("Capability 注册异常: 重复的Capability接口: %s", annotation.value()));
                }
                capabilitiesValues.add(annotation.value());
            }
        }
        return coresValues.equals(capabilitiesValues);
    }

    public void registerModule(CapabilityHolder capabilityHolder) {
        capabilityHolderInstances.put(capabilityHolder.getClass(), capabilityHolder);
    }

    record LackRecord(List<String> coreLack, List<String> capLack) {
        public boolean hasNotEmptyRecord() {
            return !coreLack.isEmpty() || !capLack.isEmpty();
        }

        public String toLackErrorMsg(String capabilityName) {
            StringBuilder sb = new StringBuilder("\n").append(capabilityName).append("\n");
            if (!coreLack.isEmpty()) {
                sb.append("缺少Core方法:").append("\n").append(coreLack).append("\n");
            }
            if (!capLack.isEmpty()) {
                sb.append("缺少Capability方法:").append("\n").append(capLack).append("\n");
            }
            return sb.toString();
        }
    }
}

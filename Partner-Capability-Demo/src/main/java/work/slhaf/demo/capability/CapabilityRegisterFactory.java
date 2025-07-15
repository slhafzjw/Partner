package work.slhaf.demo.capability;

import lombok.Setter;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import work.slhaf.demo.capability.exception.*;
import work.slhaf.demo.capability.interfaces.*;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class CapabilityRegisterFactory {

    public static volatile CapabilityRegisterFactory capabilityRegisterFactory;

    @Setter
    private Reflections reflections;

    private CapabilityRegisterFactory() {
    }

    public static CapabilityRegisterFactory getInstance() {
        if (capabilityRegisterFactory == null) {
            synchronized (CapabilityRegisterFactory.class) {
                if (capabilityRegisterFactory == null) {
                    capabilityRegisterFactory = new CapabilityRegisterFactory();
                    capabilityRegisterFactory.setReflections(getReflections());
                }
            }
        }
        return capabilityRegisterFactory;
    }

    private static Reflections getReflections() {
        //后续可替换为根据传入的启动类获取路径
        Collection<URL> urls = ClasspathHelper.forJavaClassPath();
        return new Reflections(
                new ConfigurationBuilder()
                        .setUrls(urls)
                        .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated, Scanners.SubTypes)
        );
    }

    public void registerCapabilities() {
        //检查可注册能力是否正常
        statusCheck();
        //扫描现有Capability, value为键，返回函数路由表, 函数路由表内部通过反射调用对应core的方法
        //扫描时也需要排除掉
    }

    private void statusCheck() {
        Set<Class<?>> cores = reflections.getTypesAnnotatedWith(CapabilityCore.class);
        Set<Class<?>> capabilities = reflections.getTypesAnnotatedWith(Capability.class);
        checkCountAndCapabilities(cores, capabilities);
        checkCapabilityMethods(cores, capabilities);
        checkCoordinatedMethods(capabilities);
    }

    private void checkCoordinatedMethods(Set<Class<?>> capabilities) {
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
            Set<Class<? extends BaseCognationManager>> subTypesOfAbsCM = reflections.getSubTypesOf(BaseCognationManager.class);
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

    private Set<String> getMethodsCoordinated(Set<Class<? extends BaseCognationManager>> subTypesOfAbsCM) {
        Set<String> methodsCoordinated = new HashSet<>();
        for (Class<? extends BaseCognationManager> cm : subTypesOfAbsCM) {
            Method[] methods = cm.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Coordinated.class)) {
                    methodsCoordinated.add(method.getAnnotation(Coordinated.class).capability() + "." + methodSignature(method));
                }
            }
        }
        return methodsCoordinated;
    }


    private void checkCapabilityMethods(Set<Class<?>> cores, Set<Class<?>> capabilities) {
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
                .map(this::methodSignature)
                .collect(Collectors.toSet());
        Set<String> collectedCapabilityMethods = capabilityMethods.stream()
                .filter(method -> !method.isAnnotationPresent(ToCoordinated.class))
                .map(this::methodSignature)
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

    private String methodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(method.getReturnType().getName()).append(" ");
        sb.append(method.getName()).append("(");
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append(paramTypes[i].getName());
            if (i < paramTypes.length - 1) sb.append(",");
        }
        sb.append(")").append(")");
        return sb.toString();
    }

    private HashMap<String, List<Method>> getCapabilityMethods(Set<Class<?>> capabilities) {
        HashMap<String, List<Method>> capabilityMethods = new HashMap<>();
        capabilities.forEach(capability -> {
            capabilityMethods.put(capability.getAnnotation(Capability.class).value(), Arrays.stream(capability.getMethods()).toList());
        });
        return capabilityMethods;
    }

    private void checkCountAndCapabilities(Set<Class<?>> cores, Set<Class<?>> capabilities) {
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

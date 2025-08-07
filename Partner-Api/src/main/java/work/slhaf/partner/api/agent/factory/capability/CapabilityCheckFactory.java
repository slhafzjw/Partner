package work.slhaf.partner.api.agent.factory.capability;

import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.capability.annotation.*;
import work.slhaf.partner.api.agent.factory.capability.exception.DuplicateCapabilityException;
import work.slhaf.partner.api.agent.factory.capability.exception.UnMatchedCapabilityException;
import work.slhaf.partner.api.agent.factory.capability.exception.UnMatchedCapabilityMethodException;
import work.slhaf.partner.api.agent.factory.capability.exception.UnMatchedCoordinatedMethodException;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.CapabilityFactoryContext;
import work.slhaf.partner.api.agent.util.AgentUtil;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static work.slhaf.partner.api.agent.util.AgentUtil.methodSignature;

/**
 * 执行<code>Capability</code>相关检查
 */
public class CapabilityCheckFactory extends AgentBaseFactory {

    private Reflections reflections;
    private Set<Class<?>> cores;
    private Set<Class<?>> capabilities;


    @Override
    protected void setVariables(AgentRegisterContext context) {
        CapabilityFactoryContext factoryContext = context.getCapabilityFactoryContext();
        reflections = context.getReflections();
        cores = factoryContext.getCores();
        capabilities = factoryContext.getCapabilities();
    }

    @Override
    protected void run() {
        checkCountAndCapabilities();
        checkCapabilityMethods();
        checkCoordinatedMethods();
        checkInjectCapability();
    }

    /**
     * 检查<code>@InjectCapability</code>注解是否只用在<code>@CapabilityHolder</code>所标识类的字段上
     */
    private void checkInjectCapability() {
        reflections.getFieldsAnnotatedWith(InjectCapability.class).forEach(field -> {
            if (!field.getDeclaringClass().isAssignableFrom(CapabilityHolder.class)) {
                throw new UnMatchedCapabilityException("InjectCapability 注解只能用于 CapabilityHolder 注解所在类");
            }
        });
    }

    /**
     * 检查是否包含协调方法，如果存在，则进一步检查是否存在<code>@CoordinateManager</code>提供对应的实现
     */
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
            Set<Class<?>> subTypesOfAbsCM = reflections.getTypesAnnotatedWith(CoordinateManager.class);
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

    private Set<String> getMethodsCoordinated(Set<Class<?>> classes) {
        Set<String> methodsCoordinated = new HashSet<>();
        for (Class<?> cm : classes) {
            Method[] methods = cm.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Coordinated.class)) {
                    methodsCoordinated.add(method.getAnnotation(Coordinated.class).capability() + "." + methodSignature(method));
                }
            }
        }
        return methodsCoordinated;
    }

    /**
     * 查看在<code>Capability</code>在对应的<core>CapabilityCore</core>中存在尚未实现的方法
     */
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
                .map(AgentUtil::methodSignature)
                .collect(Collectors.toSet());
        Set<String> collectedCapabilityMethods = capabilityMethods.stream()
                .filter(method -> !method.isAnnotationPresent(ToCoordinated.class))
                .map(AgentUtil::methodSignature)
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

    /**
     * 检查<code>Capability</code>和<code>CapabilityCore</code>的数量和标识是否匹配
     */
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

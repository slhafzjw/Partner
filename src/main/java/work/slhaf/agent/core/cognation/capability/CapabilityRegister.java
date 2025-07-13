package work.slhaf.agent.core.cognation.capability;

import org.reflections.Reflections;
import work.slhaf.agent.core.cognation.capability.exception.CapabilityRegisterFailedException;
import work.slhaf.agent.core.cognation.capability.interfaces.Capability;
import work.slhaf.agent.core.cognation.capability.interfaces.CapabilityCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CapabilityRegister {

    public static volatile CapabilityRegister capabilityRegister;

    private CapabilityRegister(){
    }

    public static CapabilityRegister getInstance(){
        if (capabilityRegister == null) {
            synchronized (CapabilityRegister.class) {
                if (capabilityRegister == null) {
                    capabilityRegister = new CapabilityRegister();
                }
            }
        }
        return capabilityRegister;
    }

    public void registerCapabilities(){
        //检查可注册能力是否正常
        statusCheck();
        //扫描现有Capability, value为键，返回函数路由表
    }

    private void statusCheck(){
        Reflections reflections = new Reflections("work.slhaf.agent.core");
        Set<Class<?>> cores = reflections.getTypesAnnotatedWith(CapabilityCore.class);
        Set<Class<?>> capabilities = reflections.getTypesAnnotatedWith(Capability.class);
        if (cores.size() != capabilities.size()){
            throw new CapabilityRegisterFailedException("Capability 注册异常: 已存在的CapabilityCore与Capability数量不匹配!");
        }
        if (checkValuesMatched(cores,capabilities)){
            throw new CapabilityRegisterFailedException("Capability 注册异常: 已存在的CapabilityCore与Capability不匹配!");
        }
    }


    private static boolean checkValuesMatched(Set<Class<?>> cores, Set<Class<?>> capabilities) {
        List<String> coresValues = new ArrayList<>();
        List<String> capabilitiesValues = new ArrayList<>();
        for (Class<?> core : cores) {
            CapabilityCore annotation = core.getAnnotation(CapabilityCore.class);
            if (annotation != null) {
                coresValues.add(annotation.value());
            }
        }
        for (Class<?> capability : capabilities) {
            Capability annotation = capability.getAnnotation(Capability.class);
            if (annotation != null) {
                capabilitiesValues.add(annotation.value());
            }
        }
        return coresValues.equals(capabilitiesValues);
    }
}

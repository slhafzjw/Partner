package work.slhaf.partner.api.agent.factory.module.pojo;

import lombok.Data;

@Data
public class MetaModule {
    private String name;
    private int order;
    private Class<?> clazz;
    private Object instance;
}

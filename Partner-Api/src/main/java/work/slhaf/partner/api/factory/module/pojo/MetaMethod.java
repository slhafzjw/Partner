package work.slhaf.partner.api.factory.module.pojo;

import lombok.Data;

import java.lang.reflect.Method;

@Data
public class MetaMethod {
    private int order;
    private Method method;
}

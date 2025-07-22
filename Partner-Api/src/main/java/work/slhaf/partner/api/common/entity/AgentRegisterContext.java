package work.slhaf.partner.api.common.entity;

import lombok.Data;
import org.reflections.Reflections;

@Data
public class AgentRegisterContext {
    //TODO 抽取出必要的注册工厂共用的上下文
    private Reflections reflections;
}

package work.slhaf.agent.core.cognation.submodule.dispatch.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchData {
    private LocalDateTime dateTime;
    private String userId;
    private String comment;

    //TODO 替换为<执行器>或者<插件>
    private String executor;
}

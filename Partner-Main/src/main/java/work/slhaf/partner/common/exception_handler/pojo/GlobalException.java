package work.slhaf.partner.common.exception_handler.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.core.cognation.CognationManager;
import work.slhaf.partner.core.interaction.data.context.InteractionContext;
import work.slhaf.partner.core.session.SessionManager;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class GlobalException extends RuntimeException {

    private GlobalExceptionData data;

    public GlobalException(String message) {
        super(message);
        try {
            this.data = new GlobalExceptionData();
            this.data.setExceptionTime(System.currentTimeMillis());
            this.data.setSessionManager(SessionManager.getInstance());
            this.data.setCognationManager(CognationManager.getInstance());
            this.data.setContext(InteractionContext.getInstance());
        }  catch (Exception e) {
            log.error("[GlobalException] 捕获异常, 获取数据失败");
        }
    }

}

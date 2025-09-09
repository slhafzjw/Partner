package work.slhaf.partner.runtime.exception.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.core.cognation.CognationCore;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;
import work.slhaf.partner.runtime.session.SessionManager;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class GlobalException extends RuntimeException {

    private GlobalExceptionData data;

    public GlobalException(String message) {
        super(message);
        try {
            this.data = new GlobalExceptionData();
        }  catch (Exception e) {
            log.error("[GlobalException] 捕获异常, 获取数据失败");
        }
    }

}

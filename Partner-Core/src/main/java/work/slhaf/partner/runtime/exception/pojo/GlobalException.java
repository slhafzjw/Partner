package work.slhaf.partner.runtime.exception.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class GlobalException extends RuntimeException {

    private GlobalExceptionData data;

    public GlobalException(String message) {
        super(message);
        try {
            this.data = new GlobalExceptionData();
        } catch (Exception e) {
            log.error("[GlobalException] 捕获异常, 获取数据失败");
        }
    }

}

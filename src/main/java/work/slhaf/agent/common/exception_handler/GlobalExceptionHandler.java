package work.slhaf.agent.common.exception_handler;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.exception_handler.pojo.GlobalExceptionData;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class GlobalExceptionHandler {

    private static final String EXCEPTION_STATIC_PATH = "./data/exception_snapshot/";

    public static void writeExceptionState(GlobalExceptionData exceptionData) {
        Path filePath = Paths.get(EXCEPTION_STATIC_PATH, String.valueOf(exceptionData.getExceptionTime()), ".dat");
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()));
            oos.writeObject(exceptionData);
            oos.close();
            log.warn("[GlobalExceptionHandler] 捕获异常, 已保存到: {}", filePath);
        } catch (IOException e) {
            log.error("[GlobalExceptionHandler] 捕获异常, 保存失败: ", e);
        }
    }
}

package work.slhaf.partner.common.exception.callback;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.runtime.exception.pojo.GlobalException;
import work.slhaf.partner.runtime.exception.pojo.GlobalExceptionData;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class GlobalExceptionHandler {

    private static final String EXCEPTION_STATIC_PATH = "./data/exception_snapshot/";

    public static void writeExceptionState(GlobalException exception) {
        GlobalExceptionData exceptionData = exception.getData();
        Path filePath = Paths.get(EXCEPTION_STATIC_PATH, exceptionData.getExceptionTime() + ".dat");
        try {
            Files.createDirectories(Path.of(EXCEPTION_STATIC_PATH));
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()));
            oos.writeObject(exceptionData);
            oos.close();
            log.warn("[GlobalExceptionHandler] 捕获异常, 已保存到: {}", filePath);
        } catch (IOException e) {
            log.error("[GlobalExceptionHandler] 捕获异常, 保存失败: ", e);
        }
    }

    public static GlobalExceptionData readExceptionState(String filePath) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath));
            GlobalExceptionData exceptionData = (GlobalExceptionData) ois.readObject();
            ois.close();
            log.info("[GlobalExceptionHandler] 已从: {} 读取异常快照", filePath);
            return exceptionData;
        } catch (IOException | ClassNotFoundException e) {
            log.error("[GlobalExceptionHandler] 读取异常, 读取失败: ", e);
            return null;
        }
    }
}

package work.slhaf.partner.runtime.exception;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.runtime.exception.AgentExceptionCallback;
import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException;
import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;
import work.slhaf.partner.runtime.exception.pojo.GlobalExceptionData;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class PartnerExceptionCallback implements AgentExceptionCallback {

    private static final String EXCEPTION_SNAPSHOTS_PATH = "./data/exception/snapshots/";
    private static final String EXCEPTION_LOG_PATH = "./data/exception/log/";

    @Override
    public void onRuntimeException(AgentRuntimeException exception) {
        GlobalExceptionData exceptionData = new GlobalExceptionData();
        Path filePath = Paths.get(EXCEPTION_SNAPSHOTS_PATH, exceptionData.getExceptionTime() + ".dat");
        try {
            Files.createDirectories(Path.of(EXCEPTION_SNAPSHOTS_PATH));
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()));
            oos.writeObject(exceptionData);
            oos.close();
            BufferedWriter logWriter = new BufferedWriter(new FileWriter(EXCEPTION_LOG_PATH + exceptionData.getExceptionTime() + ".log"));
            logWriter.write(exception.getMessage());
            logWriter.close();
            log.warn("[GlobalExceptionHandler] 捕获异常, 状态快照已保存到: {}", filePath);
            log.warn("[GlobalExceptionHandler] 捕获异常, 异常日志已保存到: {}", EXCEPTION_LOG_PATH + exceptionData.getExceptionTime() + ".log");
        } catch (IOException e) {
            log.error("[GlobalExceptionHandler] 捕获异常, 保存失败: ", e);
        }

    }

    @Override
    public void onFailedException(AgentLaunchFailedException exception) {
        Path filepath = Paths.get(EXCEPTION_LOG_PATH, System.currentTimeMillis() + ".log");
        try {
            Files.createDirectories(Path.of(EXCEPTION_LOG_PATH));
            BufferedWriter logWriter = new BufferedWriter(new FileWriter(EXCEPTION_LOG_PATH + System.currentTimeMillis() + ".log"));
            logWriter.write(exception.getMessage());
            logWriter.close();
            log.warn("[GlobalExceptionHandler] 捕获启动失败异常, 异常日志已保存到: {}", filepath);
        } catch (IOException ex) {
            log.error("[GlobalExceptionHandler] 捕获启动失败异常, 保存失败: ", ex);
        }
    }

}

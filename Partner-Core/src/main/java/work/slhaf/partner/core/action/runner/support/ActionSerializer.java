package work.slhaf.partner.core.action.runner.support;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.action.entity.ActionFileMetaData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.ActionSerializeFailedException;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
public class ActionSerializer {

    private final String tmpActionPath;
    private final String dynamicActionPath;

    public ActionSerializer(String tmpActionPath, String dynamicActionPath) {
        this.tmpActionPath = tmpActionPath;
        this.dynamicActionPath = dynamicActionPath;
    }

    public static String normalizeCodeType(String codeType) {
        if (codeType == null || codeType.isBlank()) {
            throw new IllegalArgumentException("codeType 不能为空");
        }
        return codeType.startsWith(".") ? codeType : "." + codeType;
    }

    private static @NotNull Path createActionDir(String baseName, Path baseDir) {
        for (int i = 0; ; i++) {
            String dirName = i == 0 ? baseName : baseName + "(" + i + ")";
            Path candidate = baseDir.resolve(dirName);
            try {
                Files.createDirectory(candidate);
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
            } catch (IOException e) {
                throw new ActionSerializeFailedException("无法创建行动目录: " + candidate.toAbsolutePath(), e);
            }
        }
    }

    public String buildTmpPath(String actionKey, String codeType) {
        return Path.of(tmpActionPath, System.currentTimeMillis() + "-" + actionKey + normalizeCodeType(codeType)).toString();
    }

    public void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException {
        log.debug("行动程序临时序列化: {}", tempAction);
        Path path = Path.of(tempAction.getLocation());
        validateTmpLocation(path, codeType);
        File file = path.toFile();
        file.createNewFile();
        Files.writeString(path, code);
        log.debug("临时序列化完毕");
    }

    private void validateTmpLocation(Path path, String codeType) throws IOException {
        String normalizedCodeType = normalizeCodeType(codeType);
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(normalizedCodeType)) {
            throw new IOException("临时文件路径与 codeType 不匹配: " + path);
        }
    }

    public void persistSerialize(MetaActionInfo metaActionInfo, ActionFileMetaData fileMetaData) {
        log.debug("行动程序持久序列化: {}", metaActionInfo);
        val baseDir = Path.of(dynamicActionPath);

        if (!Files.isDirectory(baseDir)) {
            throw new ActionSerializeFailedException("目录不存在或不可用: " + baseDir.toAbsolutePath());
        }

        val actionDir = createActionDir(fileMetaData.getName(), baseDir);
        val runTmp = actionDir.resolve("run." + fileMetaData.getExt() + ".tmp");
        val descTmp = actionDir.resolve("desc.json.tmp");
        val runFinal = actionDir.resolve("run." + fileMetaData.getExt());
        val descFinal = actionDir.resolve("desc.json");

        try {
            Files.writeString(runTmp, fileMetaData.getContent());
            Files.writeString(descTmp, JSONObject.toJSONString(metaActionInfo));
            Files.move(runTmp, runFinal, StandardCopyOption.ATOMIC_MOVE);
            Files.move(descTmp, descFinal, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            safeDelete(runTmp);
            safeDelete(descTmp);
            safeDelete(runFinal);
            safeDelete(descFinal);
            safeDelete(actionDir);
            throw new ActionSerializeFailedException("行动文件写入失败", e);
        }
        log.debug("持久序列化结束");
    }

    private void safeDelete(Path path) {
        try {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException ignored) {
        }
    }
}

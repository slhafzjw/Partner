package work.slhaf.partner.core;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigLoader;
import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.common.config.PartnerAgentConfigLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static work.slhaf.partner.common.Constant.Path.MEMORY_DATA;

@Slf4j
public abstract class PartnerCore<T extends PartnerCore<T>> extends PersistableObject {

    private final String id = ((PartnerAgentConfigLoader) AgentConfigLoader.INSTANCE).getConfig().getAgentId();

    public PartnerCore() throws IOException, ClassNotFoundException {
        createStorageDirectory();
        Path filePath = getFilePath(id);
        if (Files.exists(filePath)) {
            T deserialize = deserialize();
            setupData(deserialize, (T) this);
        } else {
            FileUtils.createParentDirectories(filePath.toFile().getParentFile());
            this.serialize();
        }
        setupHook(this);
        log.info("[{}] 注册完毕", getCoreKey());

    }

    private void setupHook(PartnerCore<T> temp) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                temp.serialize();
                log.info("[{}] 已保存", getCoreKey());
            } catch (IOException e) {
                log.error("[{}] 保存失败: ", getCoreKey(), e);
            }
        }));

    }

    private void setupData(T source, T current) {
        BeanUtil.copyProperties(source, current);
    }

    public void serialize() throws IOException {
        //先写入到临时文件，如果正常写入则覆盖原文件
        Path filePath = getFilePath(id + "-temp");
        Files.createDirectories(Path.of(MEMORY_DATA));
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()));
            oos.writeObject(this);
            oos.close();
            Path path = getFilePath(id);
            Files.move(filePath, path, StandardCopyOption.REPLACE_EXISTING);
            log.info("[{}] 已保存到: {}", getCoreKey(), path);
        } catch (IOException e) {
            Files.delete(filePath);
            log.error("[{}] 序列化保存失败: {}", getCoreKey(), e.getMessage());
        }
    }

    private T deserialize() throws IOException, ClassNotFoundException {
        Path filePath = getFilePath(id);
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(filePath.toFile()))) {
            T graph = (T) ois.readObject();
            log.info("[{}] 已从文件加载: {}", getCoreKey(), filePath);
            return graph;
        }
    }

    private Path getFilePath(String s) {
        return Paths.get(MEMORY_DATA, s + "-" + getCoreKey() + ".memory");
    }

    private void createStorageDirectory() {
        try {
            Files.createDirectories(Paths.get(MEMORY_DATA));
        } catch (IOException e) {
            log.error("[{}]创建存储目录失败: {}", getCoreKey(), e.getMessage());
        }
    }

    protected abstract String getCoreKey();
}

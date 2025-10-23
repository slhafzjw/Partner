package work.slhaf.partner.core.action.entity;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import work.slhaf.partner.core.action.exception.ActionLoadFailedException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class MetaActionInfo {
    private String key;
    private boolean io;
    private MetaActionType type;

    private Map<String, String> params;
    private String description;
    private List<String> tags;

    private List<String> preActions;
    private List<String> postActions;
    /**
     * 是否严格依赖前置行动的成功执行，若为true且前置行动失败则不执行该行动，后置任务多为触发式。默认即执行。
     */
    private boolean strictDependencies;

    private JSONObject responseSchema;

    public MetaActionInfo(File actionDir) {
        if (actionDir.isFile()) {
            throw new ActionLoadFailedException("Action directory expected, but file found: " + actionDir.getAbsolutePath());
        }
        File[] files = actionDir.listFiles();
        if (files == null || files.length == 0) {
            throw new ActionLoadFailedException("Action directory is empty: " + actionDir.getAbsolutePath());
        }
        //加载desc.json
        File desc = Path.of(actionDir.getPath(), "desc.json").toFile();
        if (!desc.exists() || desc.isDirectory()) {
            throw new ActionLoadFailedException("Action desc.json not found: " + desc.getAbsolutePath());
        }
        try {
            String s = FileUtils.readFileToString(desc, StandardCharsets.UTF_8);
            MetaActionInfo temp = JSONObject.parseObject(s, MetaActionInfo.class);
            BeanUtil.copyProperties(temp, this);
        } catch (Exception e) {
            throw new ActionLoadFailedException("Failed to load action desc.json: " + desc.getAbsolutePath(), e);
        }
        //进行必要的字段校验和初始化
        if (type == null) throw new ActionLoadFailedException("Action type missing in desc.json");
        if (params == null) params = new HashMap<>();
        if (preActions == null) preActions = new ArrayList<>();
        if (postActions == null) postActions = new ArrayList<>();
    }
}

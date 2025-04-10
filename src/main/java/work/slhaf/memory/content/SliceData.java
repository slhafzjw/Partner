package work.slhaf.memory.content;

import com.alibaba.fastjson2.JSONArray;
import lombok.Data;

@Data
public class SliceData {
    private String summary;
    private JSONArray content;
}

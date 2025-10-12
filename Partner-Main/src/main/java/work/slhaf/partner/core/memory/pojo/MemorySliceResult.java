package work.slhaf.partner.core.memory.pojo;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySliceResult extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    @JSONField(serialize = false)
    private MemorySlice sliceBefore;

    private MemorySlice memorySlice;

    @JSONField(serialize = false)
    private MemorySlice sliceAfter;
}

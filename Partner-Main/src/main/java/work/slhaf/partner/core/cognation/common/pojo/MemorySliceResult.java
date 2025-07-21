package work.slhaf.partner.core.cognation.common.pojo;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.common.serialize.PersistableObject;
import work.slhaf.partner.core.cognation.submodule.memory.pojo.MemorySlice;

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

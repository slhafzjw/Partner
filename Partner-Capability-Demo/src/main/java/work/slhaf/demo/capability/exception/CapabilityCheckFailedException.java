package work.slhaf.demo.capability.exception;

public class CapabilityCheckFailedException extends RuntimeException {
    public CapabilityCheckFailedException(String message) {
        super("Capability注册失败: "+message);
    }
}

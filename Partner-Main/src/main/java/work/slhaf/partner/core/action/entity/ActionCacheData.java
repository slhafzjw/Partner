package work.slhaf.partner.core.action.entity;

import lombok.Data;
import work.slhaf.partner.common.vector.VectorClient;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionCacheData {
    private boolean activated = false;
    private int inputMatchCount = 1;

    private float[] inputVector;
    private float[] tendencyVector;
    private String tendency;
    private double threshold = 0.75;

    private List<String> validSamples = new ArrayList<>();
    private int failedCount = 0;
    private Type type = Type.PRIMARY;

    public ActionCacheData(String tendency, float[] tendencyVector, float[] inputVector, String input) {
        this.tendency = tendency;
        this.inputVector = inputVector;
        this.tendencyVector = tendencyVector;
        this.validSamples.add(input);
    }

    /**
     * 命中缓存且评估通过时，根据输入内容的语义向量与现有的输入语义向量进行带权移动平均，以相似度为权重，同时降低失败计数，为零时置为上一级缓存类型{@link ActionCacheData.Type}
     *
     * @param inputVector  本次输入内容对应的语义向量
     * @param vectorClient 向量客户端
     * @param input        本次输入内容
     */
    public synchronized void updateAfterMatchAndPassed(float[] inputVector, VectorClient vectorClient, String input) {
        updateInputVector(inputVector, vectorClient);
        addValidSample(input);
        reduceFailedCount();
        updateType();
        addInputMatchCount();
    }

    private void updateType() {
        if (this.failedCount == 0) {
            this.type = switch (type) {
                case PRIMARY, REBUILD_V1 -> ActionCacheData.Type.PRIMARY;
                case REBUILD_V2 -> ActionCacheData.Type.REBUILD_V1;
                case REBUILD_V3 -> ActionCacheData.Type.REBUILD_V2;
            };
        }
    }

    private void reduceFailedCount() {
        this.failedCount = Math.max(this.failedCount - 1, 0);
    }

    private void addValidSample(String input) {
        if (this.validSamples.size() == 12) {
            this.validSamples.removeFirst();
        }
        this.validSamples.add(input);
    }

    private void updateInputVector(float[] inputVector, VectorClient vectorClient) {
        this.inputVector = vectorClient.weightedAverage(inputVector, this.inputVector);
    }

    /**
     * 针对命中缓存、但评估未通过的条目与输入进行处理: 增加失败计数(必要时重建并更新类型等级)、调高阈值(0.02)，由于缓存匹配但评估未通过，所以不进行带权移动平均
     *
     * @param vectorClient 向量客户端
     * @return 是否需要删除(已在REBUILD_V3状态且达到最大误判次数的)
     */
    public synchronized boolean updateAfterMatchNotPassed(VectorClient vectorClient) {
        adjustThreshold();
        addFailedCount();
        if (this.failedCount < 3) {
            return false;
        }
        if (this.type == Type.REBUILD_V3) {
            return true;
        }
        rebuildAndSwitchType(vectorClient);
        return false;
    }

    private void rebuildAndSwitchType(VectorClient vectorClient) {
        this.type = switch (this.type) {
            case PRIMARY -> {
                //样本顺序反转后，以全部样本重建
                this.validSamples = this.validSamples.reversed();
                rebuildWithSamples(vectorClient);
                yield Type.REBUILD_V1;
            }
            case REBUILD_V1 -> {
                //截取后一半样本，反转后以此重建
                List<String> temp = this.validSamples.subList(this.validSamples.size() / 2, this.validSamples.size());
                this.validSamples = temp.reversed();
                rebuildWithSamples(vectorClient);
                yield Type.REBUILD_V2;
            }
            case REBUILD_V2 -> {
                //截取后四分之一样本，反转后以此重建
                List<String> temp = this.validSamples.subList(this.validSamples.size() / 4, this.validSamples.size());
                this.validSamples = temp.reversed();
                rebuildWithSamples(vectorClient);
                yield Type.REBUILD_V3;
            }
            case REBUILD_V3 -> null;
        };
        //阈值减0.05，防止重建后一直升高
        this.threshold = Math.max(this.threshold - 0.05, 0.75);
        this.failedCount = 0;
    }

    private void rebuildWithSamples(VectorClient vectorClient) {
        for (int i = 0; i < this.validSamples.size(); i++) {
            String sample = this.validSamples.get(i);
            if (i == 0) {
                this.inputVector = vectorClient.compute(sample);
            } else {
                float[] newSampleVector = vectorClient.compute(sample);
                this.inputVector = vectorClient.weightedAverage(this.inputVector, newSampleVector);
            }
        }
    }

    private void addFailedCount() {
        this.failedCount = Math.min(this.failedCount + 1, 3);
    }

    private void adjustThreshold() {
        double newThreshold = this.threshold + 0.03;
        this.threshold = Math.min(newThreshold, 0.95);
    }

    /**
     * 针对未命中但评估通过的已存在缓存做出调整:
     * <ol>
     *     <li>
     *         若已生效，但此时未匹配到则说明阈值或者向量{@link ActionCacheData#getInputVector()}存在问题，调低阈值，同时带权移动平均
     *     </li>
     *     <li>
     *         若未生效，则只增加计数并带权移动平均
     *     </li>
     * </ol>
     *
     * @param input          本次输入内容
     * @param inputVector    本次输入内容对应的语义向量
     * @param tendencyVector 本次倾向对应的语义向量
     * @param vectorClient   向量客户端
     */
    public synchronized void updateAfterNotMatchPassed(String input, float[] inputVector, float[] tendencyVector, VectorClient vectorClient) {
        if (this.activated) {
            reduceThreshold();
            this.inputVector = vectorClient.weightedAverage(inputVector, this.inputVector);
        } else {
            addValidSample(input);
            this.tendencyVector = vectorClient.weightedAverage(tendencyVector, this.tendencyVector);
            addInputMatchCount();
        }
    }

    private void reduceThreshold() {
        double newThreshold = this.threshold - 0.02;
        this.threshold = Math.max(newThreshold, 0.75);
    }

    private void addInputMatchCount() {
        this.inputMatchCount += 1;
        if (inputMatchCount >= 6) {
            this.activated = true;
        }
    }

    public enum Type {
        PRIMARY, REBUILD_V1, REBUILD_V2, REBUILD_V3
    }
}

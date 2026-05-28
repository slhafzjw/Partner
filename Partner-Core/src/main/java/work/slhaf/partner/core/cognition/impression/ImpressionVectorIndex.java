package work.slhaf.partner.core.cognition.impression;

import work.slhaf.partner.common.vector.VectorClient;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ImpressionVectorIndex {

    private final Executor executor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "impression-vector-index");
        thread.setDaemon(true);
        return thread;
    });

    public void sync(Entity entity) {
        if (!VectorClient.status){
            return;
        }
        entity.snapshotFeatures().forEach(this::upsert);
        entity.snapshotImpressions().forEach(this::upsert);
    }

    public void upsert(String text, Entity.IndexableData indexableData){
        if (VectorClient.status){
            return;
        }
        String modelId = VectorClient.VECTOR_MODEL_ID;
        if (indexableData.getVector(modelId) != null) {
            return;
        }
        executor.execute(() -> {
            float[] vector = VectorClient.INSTANCE.compute(text);
            indexableData.updateVector(modelId,vector);
        });
    }

}

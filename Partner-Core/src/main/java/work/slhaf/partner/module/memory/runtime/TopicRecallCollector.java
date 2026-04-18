package work.slhaf.partner.module.memory.runtime;

import work.slhaf.partner.core.memory.pojo.SliceRef;

import java.util.*;

final class TopicRecallCollector {

    private static final int TOPIC_RESULT_LIMIT = 5;
    private static final int PARENT_CANDIDATE_LIMIT = 2;
    private static final int RELATED_CANDIDATE_LIMIT = 2;

    private final TopicRecallScorer scorer;

    TopicRecallCollector(TopicRecallScorer scorer) {
        this.scorer = scorer;
    }

    List<SliceRef> collect(TopicMemoryIndex topicIndex, TopicMemoryIndex.TopicTreeNode topicNode) {
        LinkedHashMap<String, ScoredSliceCandidate> candidates = new LinkedHashMap<>();
        LinkedHashMap<String, Float> relatedTopicPaths = new LinkedHashMap<>();
        collectTopicCandidates(
                topicNode,
                TopicRecallScorer.CandidateSource.PRIMARY,
                Integer.MAX_VALUE,
                candidates,
                relatedTopicPaths
        );
        collectTopicCandidates(
                topicNode.parent(),
                TopicRecallScorer.CandidateSource.PARENT,
                PARENT_CANDIDATE_LIMIT,
                candidates,
                null
        );
        for (Map.Entry<String, Float> relatedTopicEntry : relatedTopicPaths.entrySet()) {
            if (relatedTopicEntry.getValue() <= 0.0f) {
                continue;
            }
            collectTopicCandidates(
                    topicIndex.findTopicNode(relatedTopicEntry.getKey()),
                    TopicRecallScorer.CandidateSource.RELATED,
                    RELATED_CANDIDATE_LIMIT,
                    candidates,
                    null
            );
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(ScoredSliceCandidate::score)
                        .reversed()
                        .thenComparing(Comparator.comparingLong(ScoredSliceCandidate::timestamp).reversed()))
                .limit(TOPIC_RESULT_LIMIT)
                .map(ScoredSliceCandidate::sliceRef)
                .toList();
    }

    private void collectTopicCandidates(TopicMemoryIndex.TopicTreeNode topicNode,
                                        TopicRecallScorer.CandidateSource source,
                                        int limit,
                                        LinkedHashMap<String, ScoredSliceCandidate> candidates,
                                        Map<String, Float> relatedTopicPaths) {
        if (topicNode == null || topicNode.bindings().isEmpty()) {
            return;
        }
        List<TopicMemoryIndex.TopicBinding> bindings = new ArrayList<>(topicNode.bindings());
        bindings.sort(Comparator.comparingLong(TopicMemoryIndex.TopicBinding::timestamp).reversed());
        int actualLimit = limit == Integer.MAX_VALUE ? bindings.size() : Math.min(limit, bindings.size());
        for (int i = 0; i < actualLimit; i++) {
            TopicMemoryIndex.TopicBinding binding = bindings.get(i);
            if (relatedTopicPaths != null) {
                for (String relatedTopicPath : binding.relatedTopicPaths()) {
                    relatedTopicPaths.merge(
                            relatedTopicPath,
                            binding.activationProfile().getDiffusionWeight(),
                            Math::max
                    );
                }
            }
            double score = scorer.score(binding, source);
            String key = binding.sliceRef().getUnitId() + ":" + binding.sliceRef().getSliceId();
            ScoredSliceCandidate current = candidates.get(key);
            if (current == null || score > current.score()) {
                candidates.put(key, new ScoredSliceCandidate(binding.sliceRef(), binding.timestamp(), score));
            }
        }
    }

    private record ScoredSliceCandidate(SliceRef sliceRef, long timestamp, double score) {
    }
}

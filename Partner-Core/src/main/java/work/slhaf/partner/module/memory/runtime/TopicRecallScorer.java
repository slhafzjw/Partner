package work.slhaf.partner.module.memory.runtime;

import work.slhaf.partner.module.memory.pojo.ActivationProfile;

final class TopicRecallScorer {

    double score(TopicMemoryIndex.TopicBinding binding, CandidateSource source) {
        ActivationProfile profile = binding.activationProfile();
        return source.sourceScore
                + recencyScore(binding.timestamp())
                + 0.50d * profile.getActivationWeight()
                + 0.30d * profile.getContextIndependenceWeight()
                + 0.20d * source.relationFactor * profile.getDiffusionWeight();
    }

    private double recencyScore(long timestamp) {
        long ageMillis = Math.max(0L, System.currentTimeMillis() - timestamp);
        long ageDays = ageMillis / 86_400_000L;
        if (ageDays <= 1) {
            return 0.30d;
        }
        if (ageDays <= 3) {
            return 0.22d;
        }
        if (ageDays <= 7) {
            return 0.15d;
        }
        if (ageDays <= 30) {
            return 0.08d;
        }
        return 0.00d;
    }

    enum CandidateSource {
        PRIMARY(1.00f, 0.30f),
        RELATED(0.65f, 1.00f),
        PARENT(0.45f, 0.20f);

        private final float sourceScore;
        private final float relationFactor;

        CandidateSource(float sourceScore, float relationFactor) {
            this.sourceScore = sourceScore;
            this.relationFactor = relationFactor;
        }
    }
}

package work.slhaf.partner.module.memory.runtime;

import work.slhaf.partner.core.memory.pojo.SliceRef;
import work.slhaf.partner.module.memory.pojo.ActivationProfile;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

final class TopicMemoryIndex {

    private static final float DEFAULT_ACTIVATION_WEIGHT = 0.55f;
    private static final float DEFAULT_DIFFUSION_WEIGHT = 0.35f;
    private static final float DEFAULT_CONTEXT_INDEPENDENCE_WEIGHT = 0.50f;

    private final Map<String, TopicTreeNode> topicSlices = new LinkedHashMap<>();

    void recordBinding(String topicPath,
                       SliceRef sliceRef,
                       long timestamp,
                       Collection<String> relatedTopicPaths,
                       ActivationProfile activationProfile) {
        String normalizedPath = normalizeTopicPath(topicPath);
        if (normalizedPath.isBlank()) {
            return;
        }
        ensureTopicNode(normalizedPath).addBinding(
                sliceRef,
                timestamp,
                relatedTopicPaths,
                normalizeActivationProfile(activationProfile)
        );
    }

    void ensureTopicPaths(Collection<String> topicPaths) {
        if (topicPaths == null || topicPaths.isEmpty()) {
            return;
        }
        for (String topicPath : topicPaths) {
            ensureTopicNode(topicPath);
        }
    }

    void reset() {
        topicSlices.clear();
    }

    void ensureTopicPath(String topicPath) {
        String normalizedPath = normalizeTopicPath(topicPath);
        if (normalizedPath.isBlank()) {
            return;
        }
        ensureTopicNode(normalizedPath);
    }

    TopicTreeNode findTopicNode(String topicPath) {
        String normalizedPath = normalizeTopicPath(topicPath);
        if (normalizedPath.isBlank()) {
            return null;
        }
        String[] parts = normalizedPath.split("->");
        TopicTreeNode current = topicSlices.get(parts[0]);
        for (int i = 1; current != null && i < parts.length; i++) {
            current = current.children().get(parts[i]);
        }
        return current;
    }

    String getTopicTree() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, TopicTreeNode> entry : topicSlices.entrySet()) {
            collectTopicTreeLines(entry.getKey(), entry.getValue(), lines);
        }
        return String.join("\r\n", lines);
    }

    List<String> normalizeTopicPaths(Collection<String> topicPaths) {
        if (topicPaths == null || topicPaths.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String topicPath : topicPaths) {
            String normalizedPath = normalizeTopicPath(topicPath);
            if (!normalizedPath.isBlank()) {
                normalized.add(normalizedPath);
            }
        }
        return List.copyOf(normalized);
    }

    String normalizeTopicPath(String topicPath) {
        return topicPath == null ? "" : topicPath.trim();
    }

    Map<String, TopicTreeNode> roots() {
        return topicSlices;
    }

    private TopicTreeNode ensureTopicNode(String topicPath) {
        String[] parts = topicPath.split("->");
        TopicTreeNode current = topicSlices.computeIfAbsent(parts[0], ignored -> new TopicTreeNode(null));
        for (int i = 1; i < parts.length; i++) {
            TopicTreeNode parent = current;
            current = current.children.computeIfAbsent(parts[i], ignored -> new TopicTreeNode(parent));
        }
        return current;
    }

    private void collectTopicTreeLines(String path, TopicTreeNode node, List<String> lines) {
        if (node.parent() == null) {
            lines.add(path + " [root]");
        } else {
            lines.add(path + " {slices: " + node.bindings().size() + "}");
        }
        for (Map.Entry<String, TopicTreeNode> childEntry : node.children().entrySet()) {
            collectTopicTreeLines(path + "->" + childEntry.getKey(), childEntry.getValue(), lines);
        }
    }

    private ActivationProfile normalizeActivationProfile(ActivationProfile activationProfile) {
        ActivationProfile profile = activationProfile == null ? defaultActivationProfile() : new ActivationProfile(
                activationProfile.getActivationWeight(),
                activationProfile.getDiffusionWeight(),
                activationProfile.getContextIndependenceWeight()
        );
        profile.setActivationWeight(clampOrDefault(profile.getActivationWeight(), DEFAULT_ACTIVATION_WEIGHT));
        profile.setDiffusionWeight(clampOrDefault(profile.getDiffusionWeight(), DEFAULT_DIFFUSION_WEIGHT));
        profile.setContextIndependenceWeight(clampOrDefault(
                profile.getContextIndependenceWeight(),
                DEFAULT_CONTEXT_INDEPENDENCE_WEIGHT
        ));
        return profile;
    }

    private ActivationProfile defaultActivationProfile() {
        return new ActivationProfile(
                DEFAULT_ACTIVATION_WEIGHT,
                DEFAULT_DIFFUSION_WEIGHT,
                DEFAULT_CONTEXT_INDEPENDENCE_WEIGHT
        );
    }

    private float clampOrDefault(Float value, float defaultValue) {
        return value == null ? defaultValue : clamp(value);
    }

    private float clamp(float value) {
        return Math.clamp(value, 0.0f, 1.0f);
    }

    static final class TopicTreeNode {
        private final TopicTreeNode parent;
        private final Map<String, TopicTreeNode> children = new LinkedHashMap<>();
        private final CopyOnWriteArrayList<TopicBinding> bindings = new CopyOnWriteArrayList<>();

        private TopicTreeNode(TopicTreeNode parent) {
            this.parent = parent;
        }

        TopicTreeNode parent() {
            return parent;
        }

        Map<String, TopicTreeNode> children() {
            return children;
        }

        List<TopicBinding> bindings() {
            return bindings;
        }

        private void addBinding(SliceRef sliceRef,
                                long timestamp,
                                Collection<String> relatedTopicPaths,
                                ActivationProfile activationProfile) {
            for (TopicBinding binding : bindings) {
                if (Objects.equals(binding.sliceRef().getUnitId(), sliceRef.getUnitId())
                        && Objects.equals(binding.sliceRef().getSliceId(), sliceRef.getSliceId())) {
                    binding.refresh(timestamp, relatedTopicPaths, activationProfile);
                    return;
                }
            }
            bindings.add(new TopicBinding(sliceRef, timestamp, relatedTopicPaths, activationProfile));
        }
    }

    static final class TopicBinding {
        private final SliceRef sliceRef;
        private final CopyOnWriteArrayList<String> relatedTopicPaths = new CopyOnWriteArrayList<>();
        private long timestamp;
        private ActivationProfile activationProfile;

        private TopicBinding(SliceRef sliceRef,
                             long timestamp,
                             Collection<String> relatedTopicPaths,
                             ActivationProfile activationProfile) {
            this.sliceRef = sliceRef;
            this.timestamp = timestamp;
            this.activationProfile = activationProfile;
            mergeRelatedTopicPaths(relatedTopicPaths);
        }

        SliceRef sliceRef() {
            return sliceRef;
        }

        long timestamp() {
            return timestamp;
        }

        ActivationProfile activationProfile() {
            return activationProfile;
        }

        List<String> relatedTopicPaths() {
            return relatedTopicPaths;
        }

        private void refresh(long timestamp,
                             Collection<String> relatedTopicPaths,
                             ActivationProfile activationProfile) {
            this.timestamp = timestamp;
            this.activationProfile = activationProfile;
            mergeRelatedTopicPaths(relatedTopicPaths);
        }

        private void mergeRelatedTopicPaths(Collection<String> relatedTopicPaths) {
            if (relatedTopicPaths == null) {
                return;
            }
            for (String relatedTopicPath : relatedTopicPaths) {
                if (relatedTopicPath != null && !relatedTopicPath.isBlank()) {
                    this.relatedTopicPaths.addIfAbsent(relatedTopicPath);
                }
            }
        }
    }
}

package work.slhaf.partner.common.chat.pojo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PrimaryChatResponse {

    /**
     * id
     */
    private String id;
    /**
     * object
     */
    private String object;
    /**
     * created
     */
    private int created;
    /**
     * model
     */
    private String model;
    /**
     * choices
     */
    private List<ChoicesBean> choices;
    /**
     * usage
     */
    private UsageBean usage;
    /**
     * system_fingerprint
     */
    private String system_fingerprint;

    @Setter
    @Getter
    public static class UsageBean {
        /**
         * prompt_tokens
         */
        private int prompt_tokens;
        /**
         * completion_tokens
         */
        private int completion_tokens;
        /**
         * total_tokens
         */
        private int total_tokens;
        /**
         * prompt_cache_hit_tokens
         */
        private int prompt_cache_hit_tokens;
        /**
         * prompt_cache_miss_tokens
         */
        private int prompt_cache_miss_tokens;

        @Override
        public String toString() {
            return "UsageBean{" +
                   "prompt_tokens=" + prompt_tokens +
                   ", completion_tokens=" + completion_tokens +
                   ", total_tokens=" + total_tokens +
                   ", prompt_cache_hit_tokens=" + prompt_cache_hit_tokens +
                   ", prompt_cache_miss_tokens=" + prompt_cache_miss_tokens +
                   '}';
        }
    }

    @Setter
    @Getter
    public static class ChoicesBean {
        /**
         * index
         */
        private int index;
        /**
         * message
         */
        private MessageBean message;
        /**
         * logprobs
         */
        private Object logprobs;
        /**
         * finish_reason
         */
        private String finish_reason;

        @Setter
        @Getter
        public static class MessageBean {
            /**
             * role
             */
            private String role;
            /**
             * content
             */
            private String content;

        }
    }
}

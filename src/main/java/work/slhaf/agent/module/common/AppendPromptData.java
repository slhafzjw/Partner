package work.slhaf.agent.module.common;

import lombok.Data;

import java.util.HashMap;

@Data
public class AppendPromptData {
    private String comment;
    private HashMap<String,String> appendedPrompt;
}

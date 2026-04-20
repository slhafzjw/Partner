package work.slhaf.partner.module.action.executor;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.entity.intervention.MetaIntervention;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.core.cognition.ContextWorkspace;
import work.slhaf.partner.module.action.executor.entity.HistoryAction;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

class ExecutingActionBlockManager {

    private static final String SOURCE = "action_executor";

    private final ContextWorkspace contextWorkspace;

    ExecutingActionBlockManager(ContextWorkspace contextWorkspace) {
        this.contextWorkspace = contextWorkspace;
    }

    void emitActionRecoveredBlock(Set<ExecutableAction> recoveredActions) {
        Set<ExecutableActionSnapshot> snapshots = recoveredActions.stream().map(ExecutableAction::snapshot).collect(Collectors.toSet());

        String blockName = "actions_recovered";
        String emittedAt = emittedAt();
        String event = "actions_recovered";

        contextWorkspace.register(new ContextBlock(
                buildExecutingActionRecoveredFullBlock(snapshots, blockName, emittedAt, event),
                buildExecutingActionRecoveredCompactBlock(snapshots, blockName, emittedAt, event),
                buildExecutingActionRecoveredAbstractBlock(snapshots, blockName, event),
                Set.of(ContextBlock.FocusedDomain.ACTION),
                100,
                12,
                1
        ));
    }

    private @NotNull BlockContent buildExecutingActionRecoveredAbstractBlock(Set<ExecutableActionSnapshot> recoveredExecutingActions, String blockName, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "abstract", recoveredExecutingActions.size() + " executing actions recovered.");
            }
        };
    }

    private @NotNull BlockContent buildExecutingActionRecoveredCompactBlock(Set<ExecutableActionSnapshot> recoveredExecutingActions, String blockName, String emittedAt, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendListElement(document, root, "recovered_actions", "action", recoveredExecutingActions, (actionElement, action) -> {
                    appendTextElement(document, actionElement, "description", action.getDescription());
                    return Unit.INSTANCE;
                });
            }
        };
    }

    private @NotNull BlockContent buildExecutingActionRecoveredFullBlock(Set<ExecutableActionSnapshot> recoveredExecutingActions, String blockName, String emittedAt, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendListElement(document, root, "recovered_actions", "action", recoveredExecutingActions, (actionElement, action) -> {
                    appendTextElement(document, actionElement, "description", action.getDescription());
                    appendTextElement(document, actionElement, "source", action.getSource());
                    appendTextElement(document, actionElement, "executing_stage", action.getExecutingStage());
                    return Unit.INSTANCE;
                });
            }
        };
    }

    void emitStateActionTriggeredBlock(StateAction stateAction) {
        StateActionSnapshot snapshot = stateAction.snapshot();

        String blockName = buildBlockName(stateAction.getUuid());
        String emittedAt = emittedAt();
        String event = "state_action_triggered";

        contextWorkspace.register(new ContextBlock(
                buildStateActionFullBlock(snapshot, blockName, emittedAt, event),
                buildStateActionCompactBlock(snapshot, blockName, emittedAt, event),
                buildStateActionAbstractBlock(snapshot, blockName, event),
                Set.of(ContextBlock.FocusedDomain.ACTION),
                90,
                30,
                10
        ));
    }

    private @NotNull BlockContent buildStateActionAbstractBlock(StateActionSnapshot snapshot, String blockName, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "description", snapshot.getDescription());
            }
        };
    }

    private @NotNull BlockContent buildStateActionCompactBlock(StateActionSnapshot snapshot, String blockName, String emittedAt, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "reason", snapshot.getReason());
                appendTextElement(document, root, "description", snapshot.getDescription());
            }
        };
    }

    private @NotNull BlockContent buildStateActionFullBlock(StateActionSnapshot snapshot, String blockName, String emittedAt, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "reason", snapshot.getReason());
                appendTextElement(document, root, "description", snapshot.getDescription());
                appendTextElement(document, root, "source", snapshot.getSource());
                appendTextElement(document, root, "schedule_type", snapshot.getScheduleType().name().toLowerCase(Locale.ROOT));
            }
        };
    }

    void emitActionLaunchedBlock(ExecutableAction action) {
        ExecutableActionSnapshot snapshot = action.snapshot();

        String blockName = buildBlockName(action.getUuid());
        String emittedAt = emittedAt();
        String event = "executable_action_launched";

        contextWorkspace.register(new ContextBlock(
                buildActionLaunchedFullBlock(snapshot, blockName, event, emittedAt),
                buildActionCompactBlock(snapshot, blockName, event, emittedAt),
                buildActionAbstractBlock(snapshot, blockName, event),
                Set.of(ContextBlock.FocusedDomain.ACTION),
                28,
                6,
                18
        ));

    }

    private @NotNull BlockContent buildActionAbstractBlock(ExecutableActionSnapshot snapshot, String blockName, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "description", snapshot.getDescription());
            }
        };
    }

    private @NotNull BlockContent buildActionCompactBlock(ExecutableActionSnapshot snapshot, String blockName, String event, String emittedAt) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "primary_action_chain_size", snapshot.getActionChainSize());
                appendTextElement(document, root, "reason", snapshot.getReason());
                appendTextElement(document, root, "description", snapshot.getDescription());

                Schedulable.ScheduleType scheduleType = snapshot.getScheduleType();
                String scheduleContent = snapshot.getScheduleContent();

                if (scheduleType != null && scheduleContent != null) {
                    appendChildElement(document, root, "schedule_info", (element) -> {
                        appendTextElement(document, element, "schedule_type", scheduleType.name().toLowerCase(Locale.ROOT));
                        appendTextElement(document, element, "schedule_content", scheduleContent);
                        return Unit.INSTANCE;
                    });
                }
            }
        };
    }

    private @NotNull BlockContent buildActionLaunchedFullBlock(ExecutableActionSnapshot snapshot, String blockName, String event, String emittedAt) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "primary_action_chain_size", snapshot.getActionChainSize());
                appendTextElement(document, root, "reason", snapshot.getReason());
                appendTextElement(document, root, "description", snapshot.getDescription());
                appendTextElement(document, root, "source", snapshot.getSource());
                appendTextElement(document, root, "tendency", snapshot.getTendency());

                Schedulable.ScheduleType scheduleType = snapshot.getScheduleType();
                String scheduleContent = snapshot.getScheduleContent();

                if (scheduleType != null && scheduleContent != null) {
                    appendChildElement(document, root, "schedule_info", (element) -> {
                        appendTextElement(document, element, "schedule_type", scheduleType.name().toLowerCase(Locale.ROOT));
                        appendTextElement(document, element, "schedule_content", scheduleContent);
                        return Unit.INSTANCE;
                    });
                }
            }
        };
    }

    void emitActionStageSettledBlock(ExecutableAction action) {
        ExecutableActionSnapshot snapshot = action.snapshot();

        String blockName = buildBlockName(action.getUuid());
        String emittedAt = emittedAt();
        String event = "executable_action_stage_settled";

        contextWorkspace.register(new ContextBlock(
                buildActionStageFullBlock(snapshot, blockName, emittedAt, event),
                buildActionStageCompactBlock(snapshot, blockName, emittedAt, event),
                buildActionStageAbstractBlock(snapshot, blockName, event),
                Set.of(ContextBlock.FocusedDomain.ACTION),
                55,
                10,
                12
        ));
    }

    private @NotNull BlockContent buildActionStageAbstractBlock(ExecutableActionSnapshot snapshot, String blockName, String event) {
        int settledStage = snapshot.getExecutingStage();
        List<HistoryAction> history = resolveStageHistory(snapshot, settledStage);

        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "abstract", history.size() + " meta actions are resolved in stage " + settledStage);
            }
        };
    }

    private @NotNull BlockContent buildActionStageCompactBlock(ExecutableActionSnapshot snapshot, String blockName, String emittedAt, String event) {
        int settledStage = snapshot.getExecutingStage();
        List<HistoryAction> history = resolveStageHistory(snapshot, settledStage);

        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "action_chain_size", snapshot.getActionChainSize());
                appendTextElement(document, root, "abstract", history.size() + " meta actions are resolved in stage " + settledStage);
            }
        };
    }

    private @NotNull BlockContent buildActionStageFullBlock(ExecutableActionSnapshot snapshot, String blockName, String emittedAt, String event) {
        int settledStage = snapshot.getExecutingStage();
        List<HistoryAction> history = resolveStageHistory(snapshot, settledStage);

        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "action_chain_size", snapshot.getActionChainSize());
                appendTextElement(document, root, "settled_action_chain_stage", settledStage);
                appendListElement(document,
                        root,
                        "settled_meta_actions",
                        "meta_action",
                        history.subList(0, Math.min(3, history.size())),
                        (item, action) -> {
                            String primaryResult = action.result();
                            String result = primaryResult.length() > 160 ? primaryResult.substring(0, 160) : primaryResult;

                            appendTextElement(document, item, "action_key", action.actionKey());
                            appendTextElement(document, item, "result", result);
                            return Unit.INSTANCE;
                        }
                );
            }
        };
    }

    void emitActionCorrectionBlock(ExecutableAction action, String reason, List<MetaIntervention> interventions) {
        ExecutableActionSnapshot snapshot = action.snapshot();

        String blockName = buildBlockName(action.getUuid());
        String emittedAt = emittedAt();
        String event = "executable_action_correction_triggered";

        contextWorkspace.register(new ContextBlock(
                buildActionCorrectionFullBlock(snapshot, reason, interventions, blockName, emittedAt, event),
                buildActionCorrectionCompactBlock(snapshot, reason, interventions, blockName, emittedAt, event),
                buildActionCorrectionAbstractBlock(snapshot, interventions, blockName, event),
                Set.of(ContextBlock.FocusedDomain.ACTION),
                22,
                5,
                22
        ));
    }

    private List<HistoryAction> resolveStageHistory(ExecutableActionSnapshot snapshot, int settledStage) {
        List<HistoryAction> history = snapshot.getHistory().get(settledStage);
        return history == null ? List.of() : history;
    }

    private @NotNull BlockContent buildActionCorrectionAbstractBlock(ExecutableActionSnapshot snapshot, List<MetaIntervention> interventions, String blockName, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "abstract", interventions.size() + " interventions occurred after stage " + snapshot.getExecutingStage());
            }
        };
    }

    private @NotNull BlockContent buildActionCorrectionCompactBlock(ExecutableActionSnapshot snapshot, String reason, List<MetaIntervention> interventions, String blockName, String emittedAt, String event) {
        return new ActionBlockContent(blockName, SOURCE, BlockContent.Urgency.HIGH) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                Set<Integer> affectedStage = interventions.stream().map(MetaIntervention::getOrder).collect(Collectors.toSet());

                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "action_chain_size", snapshot.getActionChainSize());
                appendTextElement(document, root, "abstract", interventions.size() + " interventions occurred after stage " + snapshot.getExecutingStage() + ", stage: " + affectedStage + " are affected");
                appendTextElement(document, root, "correction_reason", reason);
            }
        };
    }

    private @NotNull BlockContent buildActionCorrectionFullBlock(ExecutableActionSnapshot snapshot, String reason, List<MetaIntervention> interventions, String blockName, String emittedAt, String event) {
        return new ActionBlockContent(blockName, SOURCE, BlockContent.Urgency.HIGH) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "action_chain_size", snapshot.getActionChainSize());
                appendTextElement(document, root, "correction_reason", reason);
                appendListElement(document,
                        root,
                        "applied_interventions",
                        "intervention",
                        interventions,
                        (item, intervention) -> {
                            appendTextElement(document, item, "type", intervention.getType().name().toLowerCase(Locale.ROOT));
                            appendTextElement(document, item, "affected_stage", intervention.getOrder());
                            appendTextElement(document, item, "applied_action_key_set", intervention.getActions());
                            return Unit.INSTANCE;
                        }
                );
            }
        };
    }

    void emitActionFinishedBlock(ExecutableAction action) {
        ExecutableActionSnapshot snapshot = action.snapshot();

        String blockName = buildBlockName(action.getUuid());
        String emittedAt = emittedAt();
        String event = "executable_action_finished";

        contextWorkspace.register(new ContextBlock(
                buildActionFinishedFullBlock(snapshot, blockName, emittedAt, event),
                buildActionFinishedFullBlock(snapshot, blockName, emittedAt, event),
                buildActionFinishedAbstractBlock(snapshot, blockName, event),
                Set.of(ContextBlock.FocusedDomain.ACTION),
                35,
                14,
                24
        ));
    }

    private @NotNull BlockContent buildActionFinishedAbstractBlock(ExecutableActionSnapshot snapshot, String blockName, String event) {
        return new ActionBlockContent(blockName, SOURCE) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "final_status", snapshot.getStatus().name().toLowerCase(Locale.ROOT));
            }
        };
    }

    private @NotNull BlockContent buildActionFinishedFullBlock(ExecutableActionSnapshot snapshot, String blockName, String emittedAt, String event) {
        return new ActionBlockContent(blockName, SOURCE, BlockContent.Urgency.HIGH) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendEventElement(document, root, event);
                appendTextElement(document, root, "emitted_at", emittedAt);
                appendTextElement(document, root, "final_status", snapshot.getStatus().name().toLowerCase(Locale.ROOT));
                appendTextElement(document, root, "result", snapshot.getResult());
            }
        };
    }

    private String emittedAt() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String buildBlockName(String actionId) {
        return "executing_action-" + actionId;
    }

    private static abstract class ActionBlockContent extends BlockContent {

        private ActionBlockContent(@NotNull String blockName, @NotNull String source, @NotNull Urgency urgency) {
            super(blockName, source, urgency);
        }

        private ActionBlockContent(@NotNull String blockName, @NotNull String source) {
            super(blockName, source);
        }

        protected void appendEventElement(@NotNull Document document, @NotNull Element root, String event) {
            appendTextElement(document, root, "event", event);
        }
    }

}

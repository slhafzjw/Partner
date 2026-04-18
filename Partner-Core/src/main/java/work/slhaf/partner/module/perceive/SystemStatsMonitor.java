package work.slhaf.partner.module.perceive;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SystemStatsMonitor extends AbstractAgentModule.Running<PartnerRunningFlowContext> {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    protected void doExecute(@NotNull PartnerRunningFlowContext context) {
        cognitionCapability.contextWorkspace().register(new ContextBlock(
                new BlockContent("system_state", "system_stats_monitor") {
                    @Override
                    protected void fillXml(@NotNull Document document, @NotNull Element root) {
                        collectReadableSystemStats().forEach((k, v) -> {
                            appendChildElement(document, root, k, element -> {
                                v.forEach((k1, v1) -> appendTextElement(document, element, k1, v1));
                                return Unit.INSTANCE;
                            });
                        });
                    }
                },
                Set.of(ContextBlock.FocusedDomain.PERCEIVE),
                100,
                0,
                0
        ));
    }

    private Map<String, Map<String, String>> collectReadableSystemStats() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        Map<String, String> basicInfo = new LinkedHashMap<>();
        basicInfo.put("os_name", System.getProperty("os.name", "N/A"));
        basicInfo.put("os_version", System.getProperty("os.version", "N/A"));
        basicInfo.put("os_arch", System.getProperty("os.arch", "N/A"));
        result.put("basic_info", basicInfo);

        Map<String, String> runtimeStatus = new LinkedHashMap<>();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        runtimeStatus.put("agent_uptime", formatDuration(runtimeMXBean.getUptime()));

        java.lang.management.OperatingSystemMXBean baseOsBean =
                ManagementFactory.getOperatingSystemMXBean();

        double systemLoadAverage = baseOsBean.getSystemLoadAverage();
        runtimeStatus.put("system_load_average", systemLoadAverage >= 0
                ? formatDouble(systemLoadAverage)
                : "N/A");

        if (baseOsBean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            double cpuLoad = osBean.getCpuLoad();
            runtimeStatus.put("system_cpu_usage", cpuLoad >= 0
                    ? formatPercent(cpuLoad * 100.0)
                    : "N/A");
        } else {
            runtimeStatus.put("system_cpu_usage", "N/A");
        }
        result.put("runtime_state", runtimeStatus);

        Map<String, String> resourceUsage = new LinkedHashMap<>();
        if (baseOsBean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            long totalPhysicalMemory = osBean.getTotalMemorySize();
            long freePhysicalMemory = osBean.getFreeMemorySize();
            long usedPhysicalMemory = totalPhysicalMemory - freePhysicalMemory;

            resourceUsage.put("physical_memory_used", formatBytes(usedPhysicalMemory));
            resourceUsage.put("physical_memory_total", formatBytes(totalPhysicalMemory));
        } else {
            resourceUsage.put("physical_memory_used", "N/A");
            resourceUsage.put("physical_memory_total", "N/A");
        }

        File root = File.listRoots() != null && File.listRoots().length > 0
                ? File.listRoots()[0]
                : null;
        if (root != null) {
            long totalSpace = root.getTotalSpace();
            long usableSpace = root.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;

            resourceUsage.put("disk_used", formatBytes(usedSpace));
            resourceUsage.put("disk_total", formatBytes(totalSpace));
        } else {
            resourceUsage.put("disk_used", "N/A");
            resourceUsage.put("disk_total", "N/A");
        }
        result.put("system_usage", resourceUsage);

        return result;
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "N/A";
        }

        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int unitIndex = 0;

        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }

        return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return String.format(Locale.ROOT, "%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        }
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
        }
        return seconds + "s";
    }

    @Override
    public int order() {
        return 1;
    }
}

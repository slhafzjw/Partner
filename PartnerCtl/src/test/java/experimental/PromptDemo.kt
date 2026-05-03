package experimental

private enum class DemoAction {
    HOME,
    RUNTIME,
    CONFIGURATION,
    FINISH,
}

private enum class DemoModule {
    WEBSOCKET_GATEWAY,
    ONEBOT_ADAPTER,
    TELEGRAM_ADAPTER,
}

fun main() {
    val prompt = _root_ide_package_.work.slhaf.partner.ctl.ui.Prompt.Companion.create()

    try {
        prompt.section("Output")
        prompt.println("Plain println")
        prompt.print("Plain print")
        prompt.println(" + println")
        prompt.info("This is an info message.")
        prompt.success("This is a success message.")
        prompt.warn("This is a warning message.")
        prompt.error("This is an error message.")
        prompt.blank()

        prompt.section("Ask")
        val required = prompt.ask(
            label = "Required value",
            required = true,
        )
        prompt.info("Required value = $required")

        val withDefault = prompt.ask(
            label = "Value with default",
            defaultValue = "default-value",
        )
        prompt.info("Value with default = $withDefault")

        val optional = prompt.ask(
            label = "Optional value",
            required = false,
        )
        prompt.info("Optional value = ${optional.ifBlank { "<blank>" }}")

        val port = prompt.ask(
            label = "Port",
            defaultValue = "8765",
        ) { value ->
            val number = value.toIntOrNull()
            when (number) {
                null -> "Port must be a number."
                !in 1..65535 -> "Port must be between 1 and 65535."
                else -> null
            }
        }
        prompt.info("Port = $port")

        prompt.section("Confirm")
        val confirmed = prompt.confirm("Continue?", defaultValue = true)
        prompt.info("Continue = $confirmed")

        prompt.section("Select")
        val action = prompt.select(
            label = "Choose next action:",
            choices = listOf(
                _root_ide_package_.work.slhaf.partner.ctl.ui.Choice("Home", DemoAction.HOME, "Configure Partner home"),
                _root_ide_package_.work.slhaf.partner.ctl.ui.Choice(
                    "Runtime",
                    DemoAction.RUNTIME,
                    "Install Partner runtime"
                ),
                _root_ide_package_.work.slhaf.partner.ctl.ui.Choice(
                    "Configuration",
                    DemoAction.CONFIGURATION,
                    "Configure model and gateway"
                ),
                _root_ide_package_.work.slhaf.partner.ctl.ui.Choice(
                    "Download release",
                    DemoAction.FINISH,
                    "Not available yet",
                    enabled = false
                ),
                _root_ide_package_.work.slhaf.partner.ctl.ui.Choice("Finish", DemoAction.FINISH),
            ),
            defaultIndex = 0,
        )
        prompt.info("Selected action = $action")

        prompt.section("Multi select")
        val modules = prompt.multiSelect(
            label = "Select modules:",
            choices = listOf(
                _root_ide_package_.work.slhaf.partner.ctl.ui.Choice(
                    "WebSocket gateway",
                    DemoModule.WEBSOCKET_GATEWAY,
                    "Local WebSocket gateway"
                ),
                _root_ide_package_.work.slhaf.partner.ctl.ui.Choice("OneBot adapter", DemoModule.ONEBOT_ADAPTER),
                _root_ide_package_.work.slhaf.partner.ctl.ui.Choice(
                    "Telegram adapter",
                    DemoModule.TELEGRAM_ADAPTER,
                    "Not available yet",
                    enabled = false
                ),
            ),
            defaultSelected = setOf(0),
        )
        prompt.info("Selected modules = ${modules.joinToString()}")

        prompt.section("Done")
        prompt.success("Prompt demo completed.")
    } catch (_: work.slhaf.partner.ctl.ui.PromptCancelledException) {
        prompt.blank()
        prompt.warn("Prompt demo cancelled.")
    }
}

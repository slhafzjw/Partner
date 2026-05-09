package work.slhaf.partner.ctl.commands

import kotlinx.serialization.json.*
import picocli.CommandLine
import work.slhaf.partner.ctl.commands.data.GatewayConfig
import work.slhaf.partner.ctl.commands.data.OpenAiCompatible
import work.slhaf.partner.ctl.commands.data.ProviderConfig
import work.slhaf.partner.ctl.commands.init.buildFromSource
import work.slhaf.partner.ctl.commands.init.configureExternalGateway
import work.slhaf.partner.ctl.commands.init.configureOpenAiCompatible
import work.slhaf.partner.ctl.commands.init.configureWebSocketGateway
import work.slhaf.partner.ctl.i18n.I18n.text
import work.slhaf.partner.ctl.support.CommandInterrupted
import work.slhaf.partner.ctl.support.inheritCommand
import work.slhaf.partner.ctl.support.loadAvailableGateway
import work.slhaf.partner.ctl.ui.Choice
import work.slhaf.partner.ctl.ui.Prompt
import work.slhaf.partner.ctl.ui.PromptCancelledException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@CommandLine.Command(
    name = "init",
    resourceBundle = "i18n.messages",
    description = [$$"${bundle:cli.init.description}"],
)
class InitCommand : Runnable {

    @CommandLine.Mixin
    lateinit var helpOptions: HelpOptions

    lateinit var home: Path

    /**
     * 运行流程:
     * 1. 检查 home、创建基础目录
     * 2. 选择本体获取方式
     *    - 手动构建
     *      1) 检查所需工具链: java、javac、mvn、git
     *      2) 拉取 git 仓库至临时目录
     *      3) 构建、并移动至 $PARTNER_HOME/resources/partner-core.jar
     * 3. gateway 配置 -> $PARTNER_HOME/config/gateway.json:
     *    - WebSocket Gateway
     *    - OneBot Gateway
     * 4. 模型配置 -> $PARTNER_HOME/config/model.json:
     *    - 提供商1
     *    - 提供商2
     * 5. 结束
     *    - 启动
     *    - 退出
     */
    override fun run() {
        val prompt = Prompt.create()
        initHome(prompt)
        installPartner(prompt)
        configureGateway(prompt)
        configureModel(prompt)
        finalize(prompt)
    }

    private fun initHome(prompt: Prompt) {
        prompt.section(text("init.home.section"))

        home = choosePartnerHome(prompt)

        Files.createDirectories(home)
        Files.createDirectories(home.resolve("resources"))
        Files.createDirectories(home.resolve("config"))

        prompt.success(text("init.home.success", home))
    }

    private fun choosePartnerHome(prompt: Prompt): Path {
        val defaultHome = resolveDefaultHome()

        while (true) {
            val selectedHome = prompt.askPath(
                label = text("init.home.label"),
                defaultValue = defaultHome,
                required = true,
                directoryOnly = true,
            )

            if (!hasHomeContent(selectedHome)) {
                return selectedHome
            }

            prompt.warn(text("init.home.duplicate.warning", selectedHome))

            when (prompt.select(
                label = text("init.home.duplicate.choice.label"),
                choices = listOf(
                    Choice(
                        text("init.home.duplicate.choice.another"),
                        HomeDuplicateChoice.ANOTHER,
                        text("init.home.duplicate.choice.another.description"),
                    ),
                    Choice(
                        text("init.home.duplicate.choice.overwrite"),
                        HomeDuplicateChoice.OVERWRITE,
                        text("init.home.duplicate.choice.overwrite.description"),
                    ),
                    Choice(text("init.home.duplicate.choice.exit"), HomeDuplicateChoice.EXIT),
                ),
                defaultIndex = 0,
            )) {
                HomeDuplicateChoice.ANOTHER -> continue
                HomeDuplicateChoice.OVERWRITE -> {
                    validateSafeHomeOverwrite(selectedHome)
                    if (!prompt.confirm(text("init.home.duplicate.confirmDelete", selectedHome), false)) {
                        continue
                    }
                    clearHomeDirectory(selectedHome)
                    return selectedHome
                }

                HomeDuplicateChoice.EXIT -> throw PromptCancelledException()
            }
        }
    }

    private fun resolveDefaultHome(): Path {
        val envHome = System.getenv("PARTNER_HOME")?.trim()
        return if (!envHome.isNullOrEmpty()) {
            Paths.get(envHome).toAbsolutePath().normalize()
        } else {
            Paths.get(System.getProperty("user.home"), ".partner").toAbsolutePath().normalize()
        }
    }

    private fun hasHomeContent(path: Path): Boolean {
        if (!Files.exists(path)) return false
        if (Files.isRegularFile(path)) return true
        if (!Files.isDirectory(path)) return false

        return Files.walk(path).use { stream ->
            stream.anyMatch { Files.isRegularFile(it) }
        }
    }

    private fun clearHomeDirectory(path: Path) {
        if (!Files.exists(path)) return
        if (!Files.isDirectory(path)) {
            throw CommandInterrupted(text("init.home.notDirectory", path))
        }

        Files.walk(path).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .filter { it != path }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private fun validateSafeHomeOverwrite(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        val userHome = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize()

        if (normalized == normalized.root) {
            throw CommandInterrupted(text("init.home.overwrite.refuseRoot", normalized))
        }

        if (normalized == userHome) {
            throw CommandInterrupted(text("init.home.overwrite.refuseUserHome", normalized))
        }

        if (normalized.nameCount < 2) {
            throw CommandInterrupted(text("init.home.overwrite.refuseBroadDirectory", normalized))
        }
    }

    private fun installPartner(prompt: Prompt) {

        prompt.section(text("init.install.section"))

        val installChoice = prompt.select(
            label = text("init.install.method.label"),
            choices = listOf(Choice(text("init.install.method.buildFromSource"), InstallChoice.BUILD_FROM_SOURCE))
        )

        when (installChoice) {
            InstallChoice.BUILD_FROM_SOURCE -> buildFromSource(home, prompt)
        }

    }

    private fun configureGateway(prompt: Prompt) {
        prompt.section(text("init.gateway.section"))

        val providedGateways = loadAvailableGateway()
        val selectedGateways = prompt.multiSelect(
            label = text("init.gateway.select.label"),
            choices = listOf(Choice(text("init.gateway.websocket.choice"), "websocket_channel")) +
                    providedGateways.map {
                        Choice(it.name, it.id)
                    }
        )

        val configuredChannels = selectedGateways.map { gateway ->
            try {
                if (gateway == "websocket_channel") {
                    return@map configureWebSocketGateway(prompt)
                } else {
                    val manifest = providedGateways.find { it.id == gateway }
                    if (manifest != null) {
                        return@map configureExternalGateway(home, prompt, manifest)
                    } else {
                        prompt.warn(text("init.gateway.warn.notFound", gateway))
                        return@map null
                    }
                }
            } catch (_: PromptCancelledException) {
                prompt.warn(text("init.gateway.warn.skipped", gateway))
                return@map null
            }
        }.filterNotNull()

        val defaultChannel = if (configuredChannels.isEmpty()) {
            prompt.info(text("init.gateway.info.skippedUseDefault"))
            return
        } else if (configuredChannels.size == 1) {
            configuredChannels.first().channelName
        } else {
            prompt.select(
                label = text("init.gateway.defaultChannel.label"),
                choices = configuredChannels.map { Choice(it.channelName, it.channelName) }
            )
        }

        prompt.info(text("init.gateway.info.defaultChannel", defaultChannel))

        val gatewayConfig = GatewayConfig(
            defaultChannel = defaultChannel,
            channels = configuredChannels
        )

        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        val gatewayStr = json.encodeToString(gatewayConfig)
        val gatewayPath = home.resolve("config").resolve("gateway.json").toAbsolutePath().normalize()
        Files.writeString(gatewayPath, gatewayStr)

        prompt.success(text("init.gateway.success.configWritten", gatewayPath))
    }

    private fun configureModel(prompt: Prompt) {
        prompt.section(text("init.model.section"))

        val modelChoices = ModelProviderChoice.entries.map { Choice(it.display, it) }

        val chosenModelProviders = mutableListOf<ProviderConfig>()

        var defaultAlreadySet = false
        while (true) {
            val choice = prompt.select(
                label = if (!defaultAlreadySet) {
                    text("init.model.provider.default.label")
                } else {
                    text("init.model.provider.label")
                },
                choices = modelChoices
            )

            val providerConfig = when (choice) {
                ModelProviderChoice.OPENAI_COMPATIBLE -> configureOpenAiCompatible(prompt, defaultAlreadySet)
                ModelProviderChoice.SKIP -> {
                    if (defaultAlreadySet) {
                        break
                    } else {
                        prompt.warn(
                            text("init.model.warn.noDefaultProvider")
                        )
                        if (prompt.confirm(text("init.model.confirm.skipConfiguration"), false)) {
                            break
                        } else {
                            null
                        }
                    }
                }
            }

            providerConfig?.let {
                chosenModelProviders.add(it)
                if (!defaultAlreadySet) {
                    defaultAlreadySet = true
                    if (!prompt.confirm(text("init.model.confirm.addAdditionalProvider"), false)) {
                        break
                    }
                }

            }
        }

        if (chosenModelProviders.isNotEmpty()) {
            val json = Json {
                prettyPrint = true
                encodeDefaults = true
            }

            val jsonObject = buildJsonObject {
                putJsonArray("providerConfigSet") {
                    chosenModelProviders.forEach {
                        add(json.encodeProviderConfig(it))
                    }
                }
                putJsonArray("runtimeConfigSet") {}
            }

            val modelPath = home.resolve("config").resolve("model.json").toAbsolutePath().normalize()
            Files.writeString(modelPath, json.encodeToString(JsonObject.serializer(), jsonObject))

            prompt.success(text("init.model.success.configWritten", modelPath))
        }
    }

    private fun finalize(prompt: Prompt) {
        prompt.section(text("init.finish.section"))

        if (!prompt.confirm(text("init.finish.confirm.startNow"), false)) {
            prompt.info(text("init.finish.info.completed"))
            return
        }

        val partnerJar = home.resolve("resources").resolve("partner-core.jar").toAbsolutePath().normalize()
        if (!Files.exists(partnerJar)) {
            throw CommandInterrupted("Partner runtime jar does not exist: $partnerJar")
        }

        prompt.info(text("init.finish.info.starting"))
        val exitCode = inheritCommand(
            command = listOf("java", "-jar", partnerJar.toString()),
            environment = mapOf("PARTNER_HOME" to home.toString()),
        )
        if (exitCode != 0) {
            throw CommandInterrupted("Partner exited with code $exitCode", exitCode)
        }
    }

    private fun Json.encodeProviderConfig(providerConfig: ProviderConfig): JsonElement {
        return when (providerConfig) {
            is OpenAiCompatible -> encodeToJsonElement(providerConfig)
            else -> error("Unsupported provider config type: ${providerConfig::class.simpleName}")
        }
    }

    private enum class InstallChoice {
        BUILD_FROM_SOURCE
    }

    private enum class ModelProviderChoice(val display: String) {
        OPENAI_COMPATIBLE("OpenAI Compatible"),
        SKIP("Skip")
    }

    private enum class HomeDuplicateChoice {
        ANOTHER,
        OVERWRITE,
        EXIT
    }

}
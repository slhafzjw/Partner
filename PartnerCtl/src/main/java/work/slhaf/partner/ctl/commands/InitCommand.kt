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
import work.slhaf.partner.ctl.support.loadAvailableGateway
import work.slhaf.partner.ctl.ui.Choice
import work.slhaf.partner.ctl.ui.Prompt
import work.slhaf.partner.ctl.ui.PromptCancelledException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@CommandLine.Command(name = "init", description = ["Initialize partner agent."])
class InitCommand : Runnable {

    lateinit var home: Path

    /**
     * 运行流程:
     * 1. 检查 home、创建基础目录
     * 2. 选择本体获取方式
     *    - 手动构建
     *      1) 检查所需工具链: java、javac、mvn、git
     *      2) 拉取 git 仓库至临时目录
     *      3) 构建、并移动至 $PARTNER_HOME/resource/partner-core.jar
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

        fun resolveDefaultHome(): Path {
            val envHome = System.getenv("PARTNER_HOME")?.trim()
            return if (!envHome.isNullOrEmpty()) {
                Paths.get(envHome).toAbsolutePath().normalize()
            } else {
                Paths.get(System.getProperty("user.home"), ".partner").toAbsolutePath().normalize()
            }
        }

        prompt.section("Initialize Partner Home")

        val defaultHome = resolveDefaultHome()

        home = prompt.askPath(
            label = "Partner Home",
            defaultValue = defaultHome,
            required = true,
            directoryOnly = true,
        )
        Files.createDirectories(home)
        Files.createDirectories(home.resolve("resource"))
        Files.createDirectories(home.resolve("config"))

        prompt.success("Partner Home initialized at $home")
    }

    private fun installPartner(prompt: Prompt) {

        prompt.section("Install Partner")

        val installChoice = prompt.select(
            label = "Choose a installation method",
            choices = listOf(Choice("Build Partner from source", InstallChoice.BUILD_FROM_SOURCE))
        )

        when (installChoice) {
            InstallChoice.BUILD_FROM_SOURCE -> buildFromSource(home, prompt)
        }

    }

    private fun configureGateway(prompt: Prompt) {
        prompt.section("Configure Gateway")

        val providedGateways = loadAvailableGateway()
        val selectedGateways = prompt.multiSelect(
            label = "Select gateway",
            choices = listOf(Choice("WebSocket Gateway", "websocket_channel")) +
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
                        prompt.warn("Could not find gateway with id $gateway")
                        return@map null
                    }
                }
            } catch (_: PromptCancelledException) {
                prompt.warn("Gateway: $gateway configuration skipped")
                return@map null
            }
        }.filterNotNull()

        val defaultChannel = if (configuredChannels.isEmpty()) {
            prompt.info("Skipped gateway configuration. Partner will use WebSocket as default gateway")
            return
        } else if (configuredChannels.size == 1) {
            configuredChannels.first().channelName
        } else {
            prompt.select(
                label = "Set default channel",
                choices = configuredChannels.map { Choice(it.channelName, it.channelName) }
            )
        }

        prompt.info("The default channel will be set to $defaultChannel")

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

        prompt.success("Gateway config written to $gatewayPath")
    }

    private fun configureModel(prompt: Prompt) {
        prompt.section("Configure Model")

        val modelChoices = ModelProviderChoice.entries.map { Choice(it.display, it) }

        val chosenModelProviders = mutableListOf<ProviderConfig>()

        var defaultAlreadySet = false
        while (true) {
            val choice = prompt.select(
                label = if (!defaultAlreadySet) {
                    "Choose default model provider type"
                } else {
                    "Choose model provider type"
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
                            "No default model provider configured. Partner may not start normally unless model.json exists " +
                                    "or PARTNER_DEFAULT_BASE_URL, PARTNER_DEFAULT_API_KEY, and PARTNER_DEFAULT_MODEL are provided at runtime."
                        )
                        if (prompt.confirm("Skip model configuration?", false)) {
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
                    if (!prompt.confirm("Add additional model provider?", false)) {
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

            prompt.success("Model config written to $modelPath")
        }
    }

    private fun finalize(prompt: Prompt) {
        TODO("Not yet implemented")
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

}
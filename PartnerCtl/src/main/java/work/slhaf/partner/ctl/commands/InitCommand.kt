package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.ctl.ui.Prompt
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
        TODO("Not yet implemented")
    }

    private fun configureGateway(prompt: Prompt) {
        TODO("Not yet implemented")
    }

    private fun configureModel(prompt: Prompt) {
        TODO("Not yet implemented")
    }

    private fun finalize(prompt: Prompt) {
        TODO("Not yet implemented")
    }

}
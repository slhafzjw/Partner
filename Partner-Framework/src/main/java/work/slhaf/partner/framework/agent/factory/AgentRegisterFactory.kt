package work.slhaf.partner.framework.agent.factory

import org.reflections.util.ClasspathHelper
import work.slhaf.partner.framework.agent.exception.FactoryExecutionException
import work.slhaf.partner.framework.agent.factory.capability.CapabilityAnnotationValidatorFactory
import work.slhaf.partner.framework.agent.factory.capability.CapabilityInjectorFactory
import work.slhaf.partner.framework.agent.factory.capability.CapabilityRegisterFactory
import work.slhaf.partner.framework.agent.factory.component.ComponentAnnotationValidatorFactory
import work.slhaf.partner.framework.agent.factory.component.ComponentInitHookExecutorFactory
import work.slhaf.partner.framework.agent.factory.component.ComponentInjectorFactory
import work.slhaf.partner.framework.agent.factory.component.ComponentRegisterFactory
import work.slhaf.partner.framework.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.framework.agent.factory.context.ShutdownHookCollectorFactory
import java.io.File
import java.net.URL

/**
 * Agent 注册总入口，按固定顺序串联各 Factory。
 *
 * 启动流程:
 * 1. 校验 Component 注解
 * 2. 注册 Component/Module
 * 3. 完成 Module 注入
 * 4. 校验 Capability 注解
 * 5. 注册 Capability 代理与路由
 * 6. 注入 Capability
 * 7. 执行 Init Hook
 * 8. 收集 Shutdown Hook
 */
object AgentRegisterFactory {
    private val urls: MutableList<URL> = mutableListOf()

    @JvmStatic
    fun launch(packageName: String) {
        urls.addAll(packageNameToURL(packageName))
        val registerContext = AgentRegisterContext(urls)
        // 1. 校验 Component 级别注解是否合规，避免注入到异常位置
        ComponentAnnotationValidatorFactory().execute(registerContext)
        // 2. 收集所有的 AgentComponent 实例
        ComponentRegisterFactory().execute(registerContext)
        // 3. 对模块与额外组件进行模块依赖注入
        ComponentInjectorFactory().execute(registerContext)
        // 4. 校验 Capability 注解与方法关系
        CapabilityAnnotationValidatorFactory().execute(registerContext)
        // 5. 根据 Capability 相关的扫描结果构造 Capability 实例
        CapabilityRegisterFactory().execute(registerContext)
        // 6. 将 Capability 实例注入至各个 AgentComponent 中
        CapabilityInjectorFactory().execute(registerContext)
        // 7. 执行模块 Init Hook 逻辑
        ComponentInitHookExecutorFactory().execute(registerContext)
        // 8. 校验并收集 Shutdown Hook 逻辑，并添加至 AgentContext 中
        ShutdownHookCollectorFactory().execute(registerContext)
    }

    @JvmStatic
    fun addScanPackage(packageName: String) {
        urls.addAll(packageNameToURL(packageName))
    }

    @JvmStatic
    fun addScanDir(externalPackagePath: String) {
        val file = File(externalPackagePath)
        if (!file.exists() || !file.isDirectory) {
            return
        }
        try {
            val files = file.listFiles() ?: return
            if (files.isEmpty()) {
                return
            }
            files.asSequence()
                .filter { it.name.endsWith(".jar") }
                .forEach { urls.add(it.toURI().toURL()) }
        } catch (e: Exception) {
            throw FactoryExecutionException(
                "Failed to load external module URLs from: $externalPackagePath",
                "agent-register-factory",
                e
            )
        }
    }

    private fun packageNameToURL(packageName: String): List<URL> {
        return ClasspathHelper.forPackage(packageName).toList()
    }
}

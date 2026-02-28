package work.slhaf.partner.api.agent.factory

import org.reflections.util.ClasspathHelper
import work.slhaf.partner.api.agent.factory.capability.CapabilityAnnotationValidatorFactory
import work.slhaf.partner.api.agent.factory.capability.CapabilityInjectorFactory
import work.slhaf.partner.api.agent.factory.capability.CapabilityRegisterFactory
import work.slhaf.partner.api.agent.factory.component.ComponentAnnotationValidatorFactory
import work.slhaf.partner.api.agent.factory.component.ComponentInitHookExecuteFactory
import work.slhaf.partner.api.agent.factory.component.ComponentInjectorFactory
import work.slhaf.partner.api.agent.factory.component.ComponentRegisterFactory
import work.slhaf.partner.api.agent.factory.config.ConfigLoaderFactory
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.api.agent.factory.context.ShutdownHookCollectorFactory
import work.slhaf.partner.api.agent.factory.exception.ExternalModuleLoadFailedException
import work.slhaf.partner.api.agent.factory.exception.ExternalModulePathNotExistException
import java.io.File
import java.net.URL

object AgentRegisterFactory {
    private val urls: MutableList<URL> = mutableListOf()

    @JvmStatic
    fun launch(packageName: String) {
        urls.addAll(packageNameToURL(packageName))
        val registerContext = AgentRegisterContext(urls)
        // 0. 加载配置
        ConfigLoaderFactory().execute(registerContext)
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
        ComponentInitHookExecuteFactory().execute(registerContext)
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
            throw ExternalModulePathNotExistException("不存在的外部模块目录: $externalPackagePath")
        }
        try {
            val files = file.listFiles()
                ?: throw ExternalModulePathNotExistException("外部模块目录为空: $externalPackagePath")
            if (files.isEmpty()) {
                throw ExternalModulePathNotExistException("外部模块目录为空: $externalPackagePath")
            }
            files.asSequence()
                .filter { it.name.endsWith(".jar") }
                .forEach { urls.add(it.toURI().toURL()) }
        } catch (e: Exception) {
            throw ExternalModuleLoadFailedException("外部模块URL获取失败: $externalPackagePath", e)
        }
    }

    private fun packageNameToURL(packageName: String): List<URL> {
        return ClasspathHelper.forPackage(packageName).toList()
    }
}


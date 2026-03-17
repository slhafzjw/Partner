package work.slhaf.partner.core.action.runner.policy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import work.slhaf.partner.core.action.exception.ActionInitFailedException

class BwrapPolicyProviderTest {

    @Test
    fun `prepare wraps commands and policy settings`() {
        val policy = ExecutionPolicy(
            mode = ExecutionPolicy.Mode.SANDBOX,
            provider = "bwrap",
            net = ExecutionPolicy.Network.DISABLE,
            inheritEnv = false,
            env = mapOf("KEY" to "VALUE"),
            workingDirectory = "/work/demo",
            readOnlyPaths = setOf("/etc"),
            writablePaths = setOf("/tmp/demo")
        )

        val wrapped = BwrapPolicyProvider.prepare(policy, listOf("python", "script.py", "--flag"))

        assertEquals("bwrap", wrapped.command)
        assertEquals("/work/demo", wrapped.workingDirectory)
        assertEquals(mapOf("KEY" to "VALUE"), wrapped.environment)
        assertEquals(
            listOf(
                "--ro-bind", "/", "/",
                "--proc", "/proc",
                "--dev", "/dev",
                "--unshare-net",
                "--chdir", "/work/demo",
                "--ro-bind", "/etc", "/etc",
                "--bind", "/tmp/demo", "/tmp/demo",
                "--", "python", "script.py", "--flag"
            ),
            wrapped.args
        )
    }

    @Test
    fun `prepare inherits environment`() {
        val policy = ExecutionPolicy(
            mode = ExecutionPolicy.Mode.SANDBOX,
            provider = "bwrap",
            net = ExecutionPolicy.Network.ENABLE,
            inheritEnv = true,
            env = mapOf("BWRAP_TEST_KEY" to "VALUE"),
            workingDirectory = null,
            readOnlyPaths = emptySet(),
            writablePaths = emptySet()
        )

        val wrapped = BwrapPolicyProvider.prepare(policy, listOf("echo", "ok"))

        assertEquals("VALUE", wrapped.environment["BWRAP_TEST_KEY"])
        assertFalse(wrapped.environment.isEmpty())
        assertFalse(wrapped.args.contains("--unshare-net"))
    }

    @Test
    fun `require command available throws ActionInitFailedException when command missing`() {
        val exception = assertThrows(ActionInitFailedException::class.java) {
            bwrapPolicyFileFacadeClass().requireCommandAvailable("definitely-not-found-bwrap-command")
        }

        assertTrue(exception.message!!.contains("definitely-not-found-bwrap-command"))
    }
}

private fun bwrapPolicyFileFacadeClass(): Class<*> {
    return Class.forName("work.slhaf.partner.core.action.runner.policy.BwrapPolicyProviderKt")
}

private fun Class<*>.requireCommandAvailable(command: String) {
    val method = getDeclaredMethod("requireCommandAvailable", String::class.java)
    method.isAccessible = true
    try {
        method.invoke(null, command)
    } catch (e: java.lang.reflect.InvocationTargetException) {
        throw (e.targetException as? RuntimeException)
            ?: (e.targetException as? Error)
            ?: RuntimeException(e.targetException)
    }
}

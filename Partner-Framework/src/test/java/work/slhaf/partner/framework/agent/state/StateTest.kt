package work.slhaf.partner.framework.agent.state

fun main() {
    testNormalStateJson()
    println()
    testCircularReference()
}

private fun testNormalStateJson() {
    val nestedMap = linkedMapOf(
        "name" to "partner",
        "enabled" to true,
        "count" to 3,
        "tags" to listOf("agent", "runtime", "state-center"),
        "meta" to linkedMapOf(
            "version" to "0.1.0",
            "experimental" to false
        )
    )

    val state = State()
    state.append("root", StateValue.obj(nestedMap))
    state.append(
        "arr",
        StateValue.arr(
            listOf(
                "hello",
                123,
                true,
                linkedMapOf(
                    "nested" to "value"
                )
            )
        )
    )

    println("=== normal state ===")
    println(state.toString())
}

private fun testCircularReference() {
    val cyclicMap = linkedMapOf<String, Any>()
    cyclicMap["name"] = "cyclic"
    cyclicMap["self"] = cyclicMap

    println("=== circular reference ===")

    try {
        val state = State()
        state.append("cyclic", StateValue.obj(cyclicMap))

        // 如果前面没有抛错，这里再触发最终 JSON 输出
        println(state.toString())
        error("Expected circular reference detection, but no exception was thrown.")
    } catch (e: IllegalStateException) {
        println("circular reference detected as expected:")
        println(e.message)
    }
}


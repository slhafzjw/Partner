package work.slhaf.partner.ctl.ui

import org.jline.builtins.Completers.DirectoriesCompleter
import org.jline.builtins.Completers.FilesCompleter
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class PromptPart(
    val text: String,
    val style: PromptStyle = PromptStyle.PLAIN,
)

enum class PromptStyle {
    PLAIN,
    DIM,
    BOLD,
    CYAN,
    GREEN,
    YELLOW,
    RED,
    BLUE,
}

class Prompt private constructor(
    private val terminal: Terminal,
    private val reader: LineReader,
) {

    companion object {
        fun create(): Prompt {
            val terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(true)
                .build()
            val reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build()
            return Prompt(terminal, reader)
        }
    }

    private val colorEnabled: Boolean =
        System.getenv("NO_COLOR") == null &&
                !(System.getenv("TERM") ?: "").equals("dumb", ignoreCase = true)

    private fun ansi(code: String, text: String): String {
        val escape = 27.toChar()
        return if (colorEnabled) "$escape[${code}m$text$escape[0m" else text
    }

    private fun bold(text: String) = ansi("1", text)

    private fun dim(text: String) = ansi("2", text)

    private fun cyan(text: String) = ansi("36", text)

    private fun green(text: String) = ansi("32", text)

    private fun yellow(text: String) = ansi("33", text)

    private fun red(text: String) = ansi("31", text)

    private fun blue(text: String) = ansi("34", text)

    private fun questionPrefix() = cyan("?")

    private fun promptLabel(label: String, defaultValue: String? = null): String {
        return renderPrompt(
            buildList {
                add(PromptPart("?", PromptStyle.CYAN))
                add(PromptPart(" $label", PromptStyle.PLAIN))
                if (defaultValue != null) {
                    add(PromptPart(" [$defaultValue]", PromptStyle.DIM))
                }
                add(PromptPart(": ", PromptStyle.PLAIN))
            }
        )
    }

    private fun renderPrompt(parts: List<PromptPart>): String {
        return parts.joinToString(separator = "") { part ->
            when (part.style) {
                PromptStyle.PLAIN -> part.text
                PromptStyle.DIM -> dim(part.text)
                PromptStyle.BOLD -> bold(part.text)
                PromptStyle.CYAN -> cyan(part.text)
                PromptStyle.GREEN -> green(part.text)
                PromptStyle.YELLOW -> yellow(part.text)
                PromptStyle.RED -> red(part.text)
                PromptStyle.BLUE -> blue(part.text)
            }
        }
    }

    fun print(message: String) {
        terminal.writer().print(message)
        terminal.writer().flush()
    }

    fun println(message: String = "") {
        terminal.writer().println(message)
        terminal.writer().flush()
    }

    fun blank() = println()

    fun section(title: String) {
        blank()
        println(cyan(bold("◆ $title")))
        println(dim("─".repeat((title.length + 2).coerceAtLeast(12))))
        blank()
    }

    fun info(message: String) = println("${blue("ℹ")}  $message")

    fun details(title: String? = null, items: List<Pair<String, String>>) {
        if (items.isEmpty()) return

        if (!title.isNullOrBlank()) {
            println(bold(title))
        }

        val width = items.maxOfOrNull { it.first.length } ?: 0
        items.forEach { (key, value) ->
            println("  ${dim(key.padEnd(width))}  $value")
        }
    }

    fun success(message: String) = println("${green("✓")}  $message")

    fun warn(message: String) = println("${yellow("⚠")}  $message")

    fun error(message: String) = println("${red("✗")}  $message")

    fun ask(
        label: String,
        defaultValue: String? = null,
        required: Boolean = true,
        validator: ((String) -> String?)? = null,
    ): String {
        while (true) {
            val input = readLine(promptLabel(label, defaultValue)).trim()
            val value = when {
                input.isNotEmpty() -> input
                defaultValue != null -> defaultValue
                !required -> ""
                else -> {
                    error("This value is required.")
                    continue
                }
            }

            val validationError = validator?.invoke(value) ?: return value
            error(validationError)
        }
    }

    fun confirm(label: String, defaultValue: Boolean = true): Boolean {
        val suffix = if (defaultValue) "[Y/n]" else "[y/N]"

        while (true) {
            when (readLine("${questionPrefix()} $label ${dim(suffix)} ").trim().lowercase()) {
                "" -> return defaultValue
                "y", "yes" -> return true
                "n", "no" -> return false
                else -> error("Please answer yes or no.")
            }
        }
    }

    fun askPath(
        label: String,
        defaultValue: Path? = null,
        required: Boolean = true,
        mustExist: Boolean = false,
        directoryOnly: Boolean = false,
        fileOnly: Boolean = false,
        currentDir: Path = Paths.get(System.getProperty("user.dir") ?: "."),
    ): Path {
        require(!(directoryOnly && fileOnly)) { "directoryOnly and fileOnly cannot both be true" }

        val normalizedCurrentDir = currentDir.toAbsolutePath().normalize()
        val pathReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(
                if (directoryOnly) {
                    DirectoriesCompleter(normalizedCurrentDir)
                } else {
                    FilesCompleter(normalizedCurrentDir)
                }
            )
            .build()

        while (true) {
            val input = readLine(pathReader, promptLabel(label, defaultValue?.toString())).trim()
            val rawValue = when {
                input.isNotEmpty() -> input
                defaultValue != null -> defaultValue.toString()
                !required -> ""
                else -> {
                    error("This value is required.")
                    continue
                }
            }

            if (rawValue.isBlank() && !required) {
                return Paths.get("")
            }

            val path = expandPath(rawValue)
            val validationError = validatePath(path, mustExist, directoryOnly, fileOnly) ?: return path
            error(validationError)
        }
    }

    fun <T> select(
        label: String,
        choices: List<Choice<T>>,
        defaultIndex: Int = 0,
    ): T {
        require(choices.isNotEmpty()) { "choices must not be empty" }
        require(defaultIndex in choices.indices) { "defaultIndex must be within choices indices" }

        return if (terminal.type == "dumb") {
            fallbackSelect(label, choices, defaultIndex)
        } else {
            try {
                interactiveSelect(label, choices, defaultIndex)
            } catch (e: PromptCancelledException) {
                throw e
            } catch (_: Throwable) {
                fallbackSelect(label, choices, defaultIndex)
            }
        }
    }

    private fun <T> interactiveSelect(
        label: String,
        choices: List<Choice<T>>,
        defaultIndex: Int,
    ): T {
        var cursor = when {
            choices[defaultIndex].enabled -> defaultIndex
            else -> choices.indexOfFirst { it.enabled }.takeIf { it >= 0 } ?: defaultIndex
        }
        var renderedLines = 0
        val oldAttributes = terminal.enterRawMode()

        try {
            while (true) {
                renderedLines = renderSelect(label, choices, cursor, renderedLines)

                when (readKey()) {
                    PromptKey.UP -> cursor = moveCursor(choices, cursor, -1)
                    PromptKey.DOWN -> cursor = moveCursor(choices, cursor, 1)
                    PromptKey.ENTER -> {
                        val choice = choices[cursor]
                        if (choice.enabled) {
                            terminal.attributes = oldAttributes
                            println()
                            return choice.value
                        }
                        beep()
                    }

                    PromptKey.CANCEL -> throw PromptCancelledException()
                    PromptKey.SPACE,
                    PromptKey.OTHER -> Unit
                }
            }
        } finally {
            terminal.attributes = oldAttributes
        }
    }

    private fun <T> fallbackSelect(
        label: String,
        choices: List<Choice<T>>,
        defaultIndex: Int,
    ): T {
        println("${questionPrefix()} $label")
        println()
        choices.forEachIndexed { index, choice ->
            val disabled = if (choice.enabled) "" else " ${dim("(unavailable)")}"
            val description = choice.description?.let { dim(" - $it") } ?: ""
            println("  ${index + 1}. ${choice.label}$disabled$description")
        }

        while (true) {
            val input = readLine(promptLabel("Choose", (defaultIndex + 1).toString())).trim()
            val selectedIndex = if (input.isEmpty()) defaultIndex else input.toIntOrNull()?.minus(1)

            if (selectedIndex != null && selectedIndex in choices.indices) {
                val choice = choices[selectedIndex]
                if (choice.enabled) return choice.value
                error("${choice.label} is not available.")
                continue
            }

            error("Please enter a number between 1 and ${choices.size}.")
        }
    }

    private fun <T> renderSelect(
        label: String,
        choices: List<Choice<T>>,
        cursor: Int,
        previousLines: Int,
    ): Int {
        if (previousLines > 0) {
            terminal.writer().print("\u001B[${previousLines}A")
            terminal.writer().print("\u001B[J")
        }

        val lines = buildList {
            add("${questionPrefix()} $label")
            add("  ${dim("↑/↓ move, Enter confirm")}")
            add("")
            choices.forEachIndexed { index, choice ->
                val pointer = if (index == cursor) cyan("❯") else " "
                val disabled = if (choice.enabled) "" else " ${dim("(unavailable)")}"
                val description = choice.description?.let { dim(" - $it") } ?: ""
                val line = "  $pointer ${choice.label}$disabled$description"
                add(if (choice.enabled) line else dim(line))
            }
        }

        lines.forEach { terminal.writer().println(it) }
        terminal.writer().flush()
        return lines.size
    }

    fun <T> multiSelect(
        label: String,
        choices: List<Choice<T>>,
        defaultSelected: Set<Int> = emptySet(),
    ): List<T> {
        require(choices.isNotEmpty()) { "choices must not be empty" }
        require(defaultSelected.all { it in choices.indices }) { "defaultSelected must only contain valid choice indices" }

        return if (terminal.type == "dumb") {
            fallbackMultiSelect(label, choices, defaultSelected)
        } else {
            try {
                interactiveMultiSelect(label, choices, defaultSelected)
            } catch (e: PromptCancelledException) {
                throw e
            } catch (_: Throwable) {
                fallbackMultiSelect(label, choices, defaultSelected)
            }
        }
    }

    private fun <T> interactiveMultiSelect(
        label: String,
        choices: List<Choice<T>>,
        defaultSelected: Set<Int>,
    ): List<T> {
        val selected = defaultSelected
            .filter { choices[it].enabled }
            .toMutableSet()
        var cursor = choices.indexOfFirst { it.enabled }.takeIf { it >= 0 } ?: 0
        var renderedLines = 0
        val oldAttributes = terminal.enterRawMode()

        try {
            while (true) {
                renderedLines = renderMultiSelect(label, choices, selected, cursor, renderedLines)

                when (readKey()) {
                    PromptKey.UP -> cursor = moveCursor(choices, cursor, -1)
                    PromptKey.DOWN -> cursor = moveCursor(choices, cursor, 1)
                    PromptKey.SPACE -> {
                        if (choices[cursor].enabled) {
                            if (!selected.add(cursor)) selected.remove(cursor)
                        } else {
                            beep()
                        }
                    }

                    PromptKey.ENTER -> {
                        terminal.attributes = oldAttributes
                        println()
                        return selected.sorted().map { choices[it].value }
                    }

                    PromptKey.CANCEL -> throw PromptCancelledException()
                    PromptKey.OTHER -> Unit
                }
            }
        } finally {
            terminal.attributes = oldAttributes
        }
    }

    private fun <T> fallbackMultiSelect(
        label: String,
        choices: List<Choice<T>>,
        defaultSelected: Set<Int>,
    ): List<T> {
        println("${questionPrefix()} $label")
        println()
        choices.forEachIndexed { index, choice ->
            val selected = if (index in defaultSelected) green("[x]") else dim("[ ]")
            val disabled = if (choice.enabled) "" else " ${dim("(unavailable)")}"
            val description = choice.description?.let { dim(" - $it") } ?: ""
            println("  ${index + 1}. $selected ${choice.label}$disabled$description")
        }
        println("  ${dim("Enter numbers separated by comma. Leave empty to use defaults.")}")

        while (true) {
            val input = readLine(promptLabel("Select")).trim()
            val selectedIndices = if (input.isEmpty()) {
                defaultSelected
            } else {
                parseIndexList(input, choices.size)
            }

            if (selectedIndices == null) {
                error("Please enter numbers between 1 and ${choices.size}, separated by comma.")
                continue
            }

            val disabled = selectedIndices.map { choices[it] }.filterNot { it.enabled }
            if (disabled.isNotEmpty()) {
                error("Unavailable choice selected: ${disabled.joinToString { it.label }}")
                continue
            }

            return selectedIndices.sorted().map { choices[it].value }
        }
    }

    private fun <T> renderMultiSelect(
        label: String,
        choices: List<Choice<T>>,
        selected: Set<Int>,
        cursor: Int,
        previousLines: Int,
    ): Int {
        if (previousLines > 0) {
            terminal.writer().print("\u001B[${previousLines}A")
            terminal.writer().print("\u001B[J")
        }

        val lines = buildList {
            add("${questionPrefix()} $label")
            add("  ${dim("↑/↓ move, Space toggle, Enter confirm")}")
            add("")
            choices.forEachIndexed { index, choice ->
                val pointer = if (index == cursor) cyan("❯") else " "
                val checked = if (index in selected) green("[x]") else dim("[ ]")
                val disabled = if (choice.enabled) "" else " ${dim("(unavailable)")}"
                val description = choice.description?.let { dim(" - $it") } ?: ""
                val line = "  $pointer $checked ${choice.label}$disabled$description"
                add(if (choice.enabled) line else dim(line))
            }
        }

        lines.forEach { terminal.writer().println(it) }
        terminal.writer().flush()
        return lines.size
    }

    private fun moveCursor(choices: List<Choice<*>>, current: Int, delta: Int): Int {
        var next = current
        repeat(choices.size) {
            next = (next + delta + choices.size) % choices.size
            if (choices[next].enabled) return next
        }
        return current
    }

    private fun readKey(): PromptKey {
        return when (val first = terminal.reader().read()) {
            -1, 3, 4 -> PromptKey.CANCEL
            10, 13 -> PromptKey.ENTER
            32 -> PromptKey.SPACE
            27 -> readEscapeKey()
            else -> when (first.toChar()) {
                'k', 'K' -> PromptKey.UP
                'j', 'J' -> PromptKey.DOWN
                'q', 'Q' -> PromptKey.CANCEL
                else -> PromptKey.OTHER
            }
        }
    }

    private fun readEscapeKey(): PromptKey {
        val second = terminal.reader().read(50L)
        if (second == -1) return PromptKey.CANCEL
        if (second != '['.code) return PromptKey.OTHER

        return when (terminal.reader().read(50L)) {
            'A'.code -> PromptKey.UP
            'B'.code -> PromptKey.DOWN
            else -> PromptKey.OTHER
        }
    }

    private fun beep() {
        terminal.writer().print('\u0007')
        terminal.writer().flush()
    }

    fun readLine(parts: List<PromptPart>): String {
        return readLine(renderPrompt(parts))
    }

    private fun readLine(prompt: String): String {
        return readLine(reader, prompt)
    }

    private fun readLine(reader: LineReader, prompt: String): String {
        return try {
            reader.readLine(prompt)
        } catch (_: UserInterruptException) {
            throw PromptCancelledException()
        } catch (_: EndOfFileException) {
            throw PromptCancelledException()
        }
    }

    private fun expandPath(value: String): Path {
        return when {
            value == "~" -> Paths.get(System.getProperty("user.home") ?: ".")
            value.startsWith("~/") -> Paths.get(System.getProperty("user.home") ?: ".", value.substring(2))
            else -> Paths.get(value)
        }.toAbsolutePath().normalize()
    }

    private fun validatePath(
        path: Path,
        mustExist: Boolean,
        directoryOnly: Boolean,
        fileOnly: Boolean,
    ): String? {
        return when {
            mustExist && !Files.exists(path) -> "Path does not exist: $path"
            directoryOnly && Files.exists(path) && !Files.isDirectory(path) -> "Path is not a directory: $path"
            fileOnly && Files.exists(path) && !Files.isRegularFile(path) -> "Path is not a regular file: $path"
            else -> null
        }
    }

    private fun parseIndexList(input: String, size: Int): Set<Int>? {
        if (input.isBlank()) return emptySet()

        return input
            .split(',', ' ')
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull()?.minus(1) ?: return null }
            .takeIf { indices -> indices.all { it in 0 until size } }
            ?.toSet()
    }
}

data class Choice<T>(
    val label: String,
    val value: T,
    val description: String? = null,
    val enabled: Boolean = true,
)

private enum class PromptKey {
    UP,
    DOWN,
    SPACE,
    ENTER,
    CANCEL,
    OTHER,
}

class PromptCancelledException : RuntimeException()

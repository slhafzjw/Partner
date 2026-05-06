package work.slhaf.partner.ctl.commands

import picocli.CommandLine

class HelpOptions {
    @CommandLine.Option(
        names = ["-h", "--help"],
        usageHelp = true,
        descriptionKey = "cli.option.help.description",
    )
    var help: Boolean = false
}

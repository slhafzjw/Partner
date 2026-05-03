package work.slhaf.partner.ctl.support

class CommandInterrupted(
    override val message: String,
    val exitCode: Int = 1,
) : RuntimeException(message)

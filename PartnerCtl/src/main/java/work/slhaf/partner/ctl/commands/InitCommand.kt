package work.slhaf.partner.ctl.commands

import picocli.CommandLine

@CommandLine.Command(name = "init")
class InitCommand : Runnable{
    override fun run() {
        println("init ok")
    }
}
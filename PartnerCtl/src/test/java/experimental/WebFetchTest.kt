package experimental

import kotlinx.serialization.json.Json
import work.slhaf.partner.ctl.support.RegistryIndex
import work.slhaf.partner.ctl.support.fetchText


fun main() {
    val str = fetchText("https://raw.githubusercontent.com/slhaf/Partner/refs/heads/master/registry/index.json")
    val index = Json.decodeFromString<RegistryIndex>(str)
    println(index)
}
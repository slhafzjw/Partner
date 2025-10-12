import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    launch {
        delay(1000)
        println(11)
    }
    launch {
        delay(1000)
        println(22)
    }
    launch {
        delay(1000)
        println(33)
    }
    launch {
    }
}

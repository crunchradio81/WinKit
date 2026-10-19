import com.crownparkcomputing.retrodos.winkit.OfflineBootPolicy
fun main(args:Array<String>) {
    val config=java.io.File(args[0]).readText()
    check(OfflineBootPolicy.allows(config,args[1]))
    println("A4 Kotlin generated-config policy: 1 passed")
}

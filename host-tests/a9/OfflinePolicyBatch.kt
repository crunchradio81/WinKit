import com.crownparkcomputing.retrodos.winkit.OfflineBootPolicy
import java.io.File

/** Batch checker for parity against independently compiled native C++ gate. */
fun main(args: Array<String>) {
    require(args.size >= 2)
    val disk = args[0]
    for (candidate in args.drop(1)) {
        val policy = OfflineBootPolicy.allows(File(candidate).readText(Charsets.UTF_8), disk)
        println("${File(candidate).name}\t${if (policy) 1 else 0}")
    }
}

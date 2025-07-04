import java.io.File
import java.io.FileWriter

fun main() {
    val set1 = mutableSetOf<String>()
    val set2 = mutableSetOf<String>()
    File("values/set1.txt").forEachLine { set1.add(it) }
    File("values/set2.txt").forEachLine { set2.add(it) }
    val diff = set1 - set2
    println("Difference between set1 and set2: $diff, size: ${diff.size}")
}
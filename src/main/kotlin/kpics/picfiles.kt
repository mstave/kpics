package kpics

import mu.KLogger
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.collections.ArrayList
import kotlin.collections.set

class PicFiles(private val basePathStr: String) : PicInterface {
    // @TODO check out delegating properties from basePath to be exposed as kpics.PicFiles properties
    private val logger: KLogger = KotlinLogging.logger {}
    var basePath: Path = Paths.get(basePathStr)
    override fun getBaseStr() = basePathStr
    var filePaths: TreeSet<Path> = TreeSet()
    //    var filePaths: TreeSet<Path> = sortedSetOf(basePath)
    //    var filePaths: MutableList<Path> = mutableListOf<Path>()
    override fun getCount(): Int {
        return filePaths.size
    }

    override val relativePathSet: HashSet<String>
        get() {
            return HashSet((getPaths().map {
                it.toString().removePrefix(
                        basePathStr + File.separator)
            }).toSet())
        }

    fun getRelativePaths(): ArrayList<String> {
        return ArrayList(filePaths.map {
            it.toString().replace(
                    "$basePathStr${File.separator}", "")
        })
    }

    override fun getFullPath(relPath: String): String? {
        for (f in filePaths) {
            if (f.toString().contains(relPath)) {
                return f.toString()
            }
        }
        return ""
    }

    //fun toPathFromRoot() : String {
//        return "${folder.pathFromRoot}$baseName.$extension"
//}
    override fun getPaths(): TreeSet<Path> {
        return filePaths
    }

    fun printFiles() {
        for (f in filePaths) {
            println(f.fileName.toString())
        }
        logger.info("test")
        logger.info(filePaths.size.toString())
    }

    init {
        logger.debug { "Starting file walk of $basePathStr" }
        for (path in Files.walk(this.basePath)) {
            if (filterImages(path)) filePaths.add(path) else {
//                println("Skipping directory: ${path}")
            }
        }
        logger.debug { "File walk complete, ${getCount()} files found" }
    }

    private fun filterImages(path: Path): Boolean {
        if (Files.isDirectory(path)) return false
        var nameS: String = path.toString()
        val extensions = listOf(
                "jpg", "jpeg",
                "3g2",
                "3gp",
                "m4v",
                "avi",
                "mp4",
                "mov",
                "psd",
                "arw",
                "png",
                "tif",
                "dng",
                "cr2", "crw",
                "gif"
                               )
        for (e in extensions) {
            if (path.toString().toLowerCase().endsWith(e)) return true
        }
//        println("Skipping extension from $path")
        return false
    }

    fun getDupes(): ConcurrentHashMap<String, ConcurrentSkipListSet<String>> {
        val md5s = ConcurrentHashMap<String, String>()
        val dupes = ConcurrentHashMap<String, ConcurrentSkipListSet<String>>()
        filePaths.parallelStream().forEach { p ->
            val f = p.toString()
            val md5 = getMD5(p)
            val existingPath = md5s.putIfAbsent(md5, f)
            if (existingPath != null) {
                val doesExist = dupes.putIfAbsent(
                        md5,
                        ConcurrentSkipListSet(setOf(f))
                                                 )
                doesExist?.let {
                    dupes[md5]!!.add(f)
                }
                dupes[md5]!!.add(existingPath)
            }
        }
        return dupes
    }

    fun RootDupes(
            dupes: ConcurrentHashMap<String, ConcurrentSkipListSet<Path>>
                 ) {
        val rootDupes = ConcurrentSkipListSet<Path>()
        dupes.forEach {
            val paths = it.value.toString()
            println(it.key + ": " + paths)
            it.value.forEach { inner ->
                if (inner.parent == basePath) {
                    rootDupes.add(inner)
                    println("ROOT " + inner.toString())
                }
            }
        }
//        rootDupes.forEach {
//            println("rm " + it.toString())
//        }
    }
}

fun getMD5(path: Path): String {
    // 59 mins with MD2, 1366 dupes
    val md = java.security.MessageDigest.getInstance("MD5")
    val bytes = md.digest(path.toFile().readBytes())
    var result = ""
    for (byte in bytes) {
        result += "%02x".format(byte)
    }
    return result
}

var dbpaths = java.util.concurrent.ConcurrentSkipListSet<Path>()
fun main(args: Array<String>) {
//    var unique =  Collections.synchronizedSet(HashSet<String>())
//    println(kpics.getMD5(Paths.get("/Users/mstave/Dropbox/Photos/pics/uk/save/one0100.jpg")))
//    println(kpics.getMD5(Paths.get("/Users/mstave/Dropbox/Photos/pics/one0100.jpg")))
//    println(kpics.getMD5(Paths.get("/Users/mstave/Dropbox/Photos/pics/one0028.jpg")))
    val unique = Collections.synchronizedMap(HashMap<String, String>())
    val pics = PicFiles("/Users/mstave/Dropbox/Photos/pics/")
    val dropbox = "/Users/mstave/Dropbox"
    val filename = java.io.File("$dropbox/pic.db").absolutePath
    val pdb = PicDB(filename)
    transaction {
        dbpaths = java.util.concurrent.ConcurrentSkipListSet(pdb.getPaths())
    }
    pics.filePaths.parallelStream().forEach {
        //        println("%s : %s ".format( kpics.getMD5(it),it.toString()))
        val md = getMD5(it)
        if (unique.containsKey(md)) println(
                "$it ${dbpaths.contains(it)} conflicts  ${unique[md]}  ${dbpaths.contains(Paths.get(unique[md]))}")
        else {
            unique[getMD5(it)] = it.toString()
        }
    }
}


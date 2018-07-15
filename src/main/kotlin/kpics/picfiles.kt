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
import java.util.zip.Adler32
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set

class PicFiles(private val basePathStr: String) : PicInterface {
    // @TODO check out delegating properties from basePath to be exposed as kpics.PicFiles properties
    private val logger: KLogger = KotlinLogging.logger {}
    var basePath: Path = Paths.get(basePathStr)
    var dupeFiles: HashSet<HashSet<String>>? = null
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

    fun getDirStats(): HashMap<String, Pair<Int, Int>> { // path: totalFileCount, dupeFileCount
        val dupes = getDupes()
        var result = HashMap<String, Pair<Int, Int>>()
        filePaths.forEach { path ->
            result[path.parent.toString()] =
                    result[path.parent.toString()]?.let { Pair(it.first + 1, 0) } ?: Pair(1, 0)
        }
        dupes.flatten().forEach { pathStr ->
            result[Paths.get(pathStr).parent.toString()] = Pair(
                    result[Paths.get(pathStr).parent.toString()]!!.first,
                    result[Paths.get(pathStr).parent.toString()]!!.second + 1)
        }
        return result
    }

    fun getDupesForFile(fName: String): ArrayList<String> {
        var result = ArrayList<String>()
        val dupes = getDupes()
        dupes.forEach { dupeList ->
            if (dupeList.contains(fName)) {
                result.addAll(dupeList.filter {
                    it != fName
                })
            }
            if (dupeList.contains("/Users/mstave/Dropbox/Photos/pics/g1.jpg")) {
//                if (dupeList.contains("/Users/mstave/Dropbox/Photos/pics/000adel332.jpg")) {
                println("Break! $dupeList")
            }
        }
        return result
    }

    fun getDupesForDir(dir: String): ArrayList<String> {
        var result = ArrayList<String>()
        val dupes = getDupes().flatten()
        val files = File(dir).listFiles()
        files.forEach {
            if (it.isFile) {
                if (dupes.contains(it.toString())) {
                    result.add(it.toString())
                } else {
                    println(it.toString())
                }
            }
        }

        println("------------")
        return result
    }

    fun getDirsWithAllDupes(): ArrayList<String> {
        var retval = ArrayList<String>()
        val res = getDirStats()
        res.forEach {
            if (it.value.first == it.value.second) {
                retval.add(it.key)
            }
        }
        return retval
    }

    @Synchronized
    fun getDupes(): HashSet<HashSet<String>> {
        // memoize, since this may be an expensive operation
        dupeFiles?.let {
            return it
        }
        val md5s = ConcurrentHashMap<String, String>()
        val sizes = ConcurrentHashMap<String, String>()
        val dupeSizes = ConcurrentHashMap<String, ConcurrentSkipListSet<String>>()
        filePaths.parallelStream().forEach { p ->
            val f = p.toString()
            val strLen = p.toFile().length().toString()
            val existingPath = sizes.putIfAbsent(strLen, f)
            if (existingPath != null) {
                val doesExist = dupeSizes.putIfAbsent(
                        strLen,
                        ConcurrentSkipListSet(setOf(f).plus(existingPath)))
                doesExist?.let {
                    dupeSizes[strLen]!!.add(f)
                }
            }
        }
        val dupeMD5s = ConcurrentHashMap<String, ConcurrentSkipListSet<String>>()
        println("After initial pass: ${dupeSizes.size}")
        dupeSizes.values.parallelStream().forEach { pathList ->
            pathList.forEach { pathStr ->
                val md5 = getMD5(Paths.get(pathStr))
                val existingMD5 = md5s.putIfAbsent(md5, pathStr)
                if (existingMD5 != null) {
                    val doesExistMD5 =
                            dupeMD5s.putIfAbsent(md5, ConcurrentSkipListSet(setOf(pathStr).plus(
                                    existingMD5)))
                    doesExistMD5?.let {
                        dupeMD5s[md5]!!.add(pathStr)
                    }
                }
            }
        }
        val ret = HashSet<HashSet<String>>()
        dupeMD5s.values.forEach {
            ret.add(it.toHashSet())
        }
        dupeFiles = ret
        return ret
    }

    fun getDupeFileList(): ArrayList<String> {
        var ret = ArrayList<String>()
        getDupes().flatten().forEach {
            ret.add("${it} : ${getDupesForFile(it).toString()}")
        }
        ret.sort()
        return ret
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

fun getChecksum(path: Path): String {
    val checksum = Adler32()
//    val checksum = CRC32()
    val bytes = path.toFile().readBytes()
    checksum.update(bytes, 0, bytes.size)
    val lngChecksum = checksum.value

    return "$lngChecksum"
}

fun getMD5(path: Path): String {
    // 59 mins with MD2, 1366 dupes
//    val md = java.security.MessageDigest.getInstance("SHA")
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


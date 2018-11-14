package kpics

import mu.KLogger
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set

typealias DirDupeStats = HashMap<String, Pair<Int, Int>>

class LocalPicFiles(private val basePathStr: String) : AbstractPicCollection() {
    // @TODO check out delegating properties from basePath to be exposed as kpics.LocalPicFiles properties
    private val logger: KLogger = KotlinLogging.logger {}
    private var dupesChecked = false
    private var _dupeFiles = HashSet<HashSet<String>>()  // in order to facilitate multithreaded checking
    var basePath: Path = Paths.get(basePathStr)
    var dupeFiles: HashSet<HashSet<String>>? = null
        get() = getDupes()
    var filePaths: TreeSet<Path> = TreeSet()
    override val count: Int
        get() = filePaths.size
    override val baseStr: String
        get() {
            return if (basePathStr.endsWith((File.separator))) {
                basePathStr
            } else {
                basePathStr + File.separator
            }
        }
    override val relativePaths: TreeSet<String?>
        get() {
            return TreeSet((paths.map {
                toRelativePathStr(it)
            }))
        }

    override fun getFullPath(relPath: String): String? {
        for (f in filePaths) {
            if (f.toString().contains(relPath)) {
                return f.toString()
            }
        }
        return ""
    }

    override val paths: TreeSet<Path>
        get() = filePaths

    fun printFiles() {
        for (f in paths) {
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
        logger.debug { "File walk complete, $count files found" }
    }

    private fun filterImages(path: Path): Boolean {
        if (Files.isDirectory(path)) return false
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

    fun getDirStats(): DirDupeStats { // path: totalFileCount, dupeFileCount
        val dupes = getDupes()
        dupes?.forEach { outer ->
            println("-->")
            outer.forEach {
                println(it)
            }
        }
        val result = HashMap<String, Pair<Int, Int>>()
        filePaths.forEach { path ->
            result[path.parent.toString()] =
                    result[path.parent.toString()]?.let { Pair(it.first + 1, 0) } ?: Pair(1, 0)
        }
        dupes?.flatten()?.forEach { pathStr ->
            result[Paths.get(pathStr).parent.toString()] = Pair(
                    result[Paths.get(pathStr).parent.toString()]!!.first,
                    result[Paths.get(pathStr).parent.toString()]!!.second + 1)
        }
        return result
    }

    fun getDupesForFile(fName: String): ArrayList<String> {
        val result = ArrayList<String>()
        val dupes = getDupes()
        dupes?.forEach { dupeList ->
            if (dupeList.contains(fName)) {
                result.addAll(dupeList.filter {
                    it != fName
                })
            }
        }
        return result
    }

    fun getDupesForDir(dir: String): ArrayList<String> {
        val result = ArrayList<String>()
        val dupes = getDupes()?.flatten()
        val files = File(dir).listFiles()
        files.forEach { it ->
            if (it.isFile) {
                dupes?.let {
                    if (it.contains(it.toString())) {
                        result.add(it.toString())
                    } else {
                        println(it.toString())
                    }
                }
            }
        }
        println("------------")
        return result
    }

    fun getDirsWithAllDupes(): ArrayList<String> {
        val retval = ArrayList<String>()
        val res = getDirStats()
        println(res)
        res.forEach {
            if (it.value.first == it.value.second) {
                retval.add(it.key)
            }
        }
        return retval
    }

    /**
     * @return  <<file1 file2><file3 file4 file5> ... >
     */
    @Synchronized
    fun getDupes(): HashSet<HashSet<String>>? {
        // memoize, since this may be an expensive operation
        if (dupesChecked)
            return _dupeFiles
        val dupeSizes = getDupeSizes(filePaths)
        logger.info("Based upon size, dupe count: ${dupeSizes.size}")
        val dupeMd5 = getDupeHash(dupeSizes)
        dupeMd5.values.forEach {
            _dupeFiles.add(it.toHashSet())
        }
        logger.info("After checking MD5s of size-based dupes : ${dupeSizes.size}")
        dupesChecked = true
        return _dupeFiles
    }

    /**
     * Find dupes in a list of files based upon their size
     * @param paths set of paths to files to be analyzed
     * @return map<file size,set<path str>>
     */
    private fun getDupeSizes(paths: Set<Path>):
            HashMap<Long, ConcurrentSkipListSet<String>> {
        val sizes = ConcurrentHashMap<Long, String>() // <file length> -> <file path with that length>
        val dupeSizes = ConcurrentHashMap<Long, ConcurrentSkipListSet<String>>() // <file length> -> <set of paths>
        logger.info("starting first pass for dupes, by size")
        paths.parallelStream().forEach { p ->
            val pathString = p.toString()
            val fileSize = p.toFile().length()
            val existingPath = sizes.putIfAbsent(fileSize, pathString)
            if (existingPath != null) {  // a file of this length has been found already
                val doesExist = dupeSizes.putIfAbsent( // 1st dupe for this size
                        fileSize,
                        ConcurrentSkipListSet(setOf(pathString).plus(existingPath)))
                doesExist?.let {
                    dupeSizes[fileSize]!!.add(pathString) // this is the 3rd+ time we've seen this length
                }
            }
        }
        logger.info("complete: first pass for dupes, by size")
        return HashMap(dupeSizes)
    }

    /**
     * @param priorDupes input hashmap of <<filesize -> list of files of that size>>
     * @return <<md5value1 : <fileA, fileB>>,<md5value2 : <fileC, fileD, fileE>>>
     */
    private fun getDupeHash(priorDupes: Map<Long, ConcurrentSkipListSet<String>>):
            HashMap<String, ConcurrentSkipListSet<String>> {
        val dupeMD5s = ConcurrentHashMap<String, ConcurrentSkipListSet<String>>()
        logger.info("starting MD5 check")
        // for each set of files that are of the same size
        priorDupes.values.parallelStream().forEach { pathList ->
//        priorDupes.values.forEach { pathList ->
            // different bucket of each unique filesize
            val md5s = ConcurrentHashMap<String, String>()
            pathList.forEach { pathStr ->
//                pathList.parallelStream().forEach { pathStr ->
                val md5 = calculateHash(Paths.get(pathStr).toFile())
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
        logger.info("complete: MD5 check")
        return HashMap(dupeMD5s)
    }

    fun getDupeFileList(): List<String>? {
        return getDupes()?.flatten()?.map { "$it : ${getDupesForFile(it)}" }?.sorted()
    }

    fun rootDupes(
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
    }
}

fun calculateHash(updateFile: File): String {
    var hash = ""
    val istream: InputStream
    try {
        istream = FileInputStream(updateFile)
    } catch (e: FileNotFoundException) {
        println("Exception while getting digest for ${updateFile.name}: $e")
        return ""
    }
    val buffer = ByteArray(  256 * 1024)
    var amtRead: Int
    try {
        do {
            amtRead = istream.read(buffer)
            if (amtRead <= 0) {
                break
            }
            hash += buffer.contentHashCode().toString()
        } while (true)
        return hash
    } catch (e: IOException) {
        throw RuntimeException("Error reading ${updateFile.name}", e)
    } finally {
        try {
            istream.close()
        } catch (e: IOException) {
            println("Exception while getting digest$e")
        }
    }
}



fun getMD5(path: Path): String {
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
    val pics = LocalPicFiles("/Users/mstave/Dropbox/Photos/pics/")
    val dropbox = "/Users/mstave/Dropbox"
    val filename = java.io.File("$dropbox/pic.db").absolutePath
    val pdb = LightroomDB(filename)
    transaction {
        dbpaths = java.util.concurrent.ConcurrentSkipListSet(pdb.paths)
    }
    pics.filePaths.forEach {
        //        println("%s : %s ".format( kpics.getMD5(it),it.toString()))
        val md = getMD5(it)
        if (unique.containsKey(md)) println(
                "$it ${dbpaths.contains(it)} conflicts  ${unique[md]}  ${dbpaths.contains(Paths.get(unique[md]))}")
        else {
            unique[getMD5(it)] = it.toString()
        }
    }
}


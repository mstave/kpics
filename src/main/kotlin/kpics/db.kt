package kpics

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Slf4jSqlLogger
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteDataSource
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.util.*
import kotlin.collections.ArrayList

const val query = """
    SELECT afile.ID_local,
		  afile.ID_global,
		  afile.baseName,
		  afile.extension,
		  afile.folder,
		  aroot.absolutePath,
		  afolder.pathFromRoot
		FROM main.kpics.AgLibraryFile afile
		  INNER JOIN main.kpics.AgLibraryFolder afolder
			ON  afile.folder = afolder.ID_local
		  INNER JOIN main.kpics.AgLibraryRootFolder aroot
			ON  afolder.rootFolder = aroot.ID_local
     """

object AgLibraryRootFolder : IntIdTable(columnName = "id_local") {
    val id_global = varchar("id_global", 32)
    val absolutePath = varchar("absolutePath", 256)
    val name = varchar("name", 256)
    val relativePathFromCatalog = varchar("relativePathFromCatalog", 256)
}

// Cities
object AgLibraryFolder : IntIdTable(columnName = "id_local") {
    val id_global = varchar("id_global", 32)
    val rootFolder = reference("rootFolder", AgLibraryRootFolder)
    val pathFromRoot = varchar("pathFromRoot", 256)
}

// Users
object AgLibraryFile : IntIdTable(columnName = "id_local") {
    val id_global = varchar("id_global", 32)
    val baseName = varchar("baseName", 128)
    val extension = varchar("extension", 128)
    val folder = reference("folder", AgLibraryFolder)
    val modTime = long("modTime")
    val externalModTime = long("externalModTime")
}

// City
class LibFolder(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LibFolder>(AgLibraryFolder)

    val path by LibFile referrersOn AgLibraryFile.folder
    var rootFolder by LibRootFolder referencedOn AgLibraryFolder.rootFolder
    var pathFromRoot by AgLibraryFolder.pathFromRoot
    override fun toString(): String {
        //return rootFolder.toString()
        return "$rootFolder$pathFromRoot"
    }
}

class LibRootFolder(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LibRootFolder>(AgLibraryRootFolder)

    var absolutePath by AgLibraryRootFolder.absolutePath
    val rootFolder by LibFolder referrersOn AgLibraryFolder.rootFolder
    override fun toString(): String {
        return absolutePath
    }
}

// User
fun fromCrazyAppleDate(crazy: Long): java.util.Date {
    val crazyAppleDataConstant = 978307200L
    return java.util.Date(1000 * (crazy + crazyAppleDataConstant))
}

open class LibFile(id: EntityID<Int>) : IntEntity(id), Comparable<LibFile> {
    companion object : IntEntityClass<LibFile>(AgLibraryFile)

    var folder by LibFolder referencedOn AgLibraryFile.folder
    var baseName by AgLibraryFile.baseName
    var extension by AgLibraryFile.extension
    var modTime by AgLibraryFile.modTime
    var externalModTime by AgLibraryFile.externalModTime
    val modDateTime: java.util.Date
        get() = fromCrazyAppleDate(modTime)
    val externalModDateTime: java.util.Date
        get() = fromCrazyAppleDate(externalModTime)

    fun toPathFromRoot(): String {
        return transaction {
            return@transaction "${folder.pathFromRoot}$baseName.$extension"
        }
    }

    fun toPath(): Path {
        return Paths.get(toString())
    }

    override fun compareTo(other: LibFile): Int {
        return toPath().compareTo(other.toPath())
    }

    override fun toString(): String {
        return "$folder$baseName.$extension"
//        return "$folder$baseName.$extension @ ${modDateTime}"
    }
}

class PicDB(internal val fName: String?) : AbstractPicCollection() {
    private var libFile = LibFile
    private val ds = SQLiteDataSource()
    override val baseStr
        get() = fName
    private var db: Database

    init {
        ds.url = "jdbc:sqlite:$fName"
        require(File(fName).isFile)
        db = Database.connect(ds)
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    }

    fun getAll(): ArrayList<LibFile>? {
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        return transaction(db) {
            return@transaction ArrayList(libFile.all().toList())
        }
    }

    override val paths: TreeSet<Path>
        get() {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            return transaction(db) {
                return@transaction TreeSet(getAll()?.map { it.toPath() })
            }
        }
    override val relativePathSet: HashSet<String>?
        get() {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            return transaction(db) {
                return@transaction HashSet(getAll()?.map { it.toPathFromRoot() }?.toSet())
            }
        }

    fun containsName(fName: String): Boolean {
        val full = getFullPath(fName)
        return full != null
    }

    override fun getFullPath(relPath: String): String? {
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        var fPath: String? = null
        xact {
            val findRes = libFile.find {
                (AgLibraryFile.baseName eq (relPath.split(".")[0])) and
                        (AgLibraryFile.extension eq relPath.split(".")[1])
            }
            if (!findRes.empty()) {
                fPath = findRes.first().toString()
            }
        }
        return fPath
    }

    fun contains(p: Path?): Boolean {
        println(paths)
        return paths.contains(p)
    }

    fun containsRelPath(relPath: Path): Boolean {
        var count = 0
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        xact {
            var extension = ""
            val i = relPath.fileName.toString().lastIndexOf('.')
            if (i >= 0) {
                extension = relPath.fileName.toString().substring(i + 1)
            }
            val baseName = relPath.fileName.toString().substring(0, i)
            count = libFile.find {
                (AgLibraryFile.baseName eq baseName) and
                        (AgLibraryFile.extension eq extension)
            }.count()
        }
        return count > 0
    }

    override val count: Int
        get() = {
            var count = 0
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            xact {
                count = libFile.all().count()
            }
            count
        }()

    fun xact(body: () -> Unit) {
        transaction(db) {
            body()
        }
    }
}

fun main(args: Array<String>) {
    val dropbox = "/Users/mstave/dropbox"
    val filename = java.io.File("$dropbox/pic.db").absolutePath
    val ds = SQLiteDataSource()
    ds.url = "jdbc:sqlite:$filename"
    Database.connect(ds)

    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    transaction {
        //        logger.addLogger(StdOutSqlLogger)
        logger.addLogger(Slf4jSqlLogger)
        for (pic in LibFile.all()) {
//            for (pic in kpics.AgLibraryFile.selectAll()) {
            if (pic.modDateTime != pic.externalModDateTime) {
                println("They are different")
                println(pic)
            }
        }
//        println("Extensions: ${kpics.LibFile.find { kpics.AgLibraryFile.extension eq "MP4"}.joinToString { it.baseName }}")
        val result = LibFolder.wrapRows(
                AgLibraryFile.innerJoin(AgLibraryFolder).select { AgLibraryFile.extension eq "MP4" }
                                       ).toList()
//        println(result)
//        println(kpics.AgLibraryFile.slice(kpics.AgLibraryFile.baseName, kpics.AgLibraryFile.extension).select {
//            kpics.AgLibraryFile.extension eq "MP4"}.orderBy(kpics.AgLibraryFile.baseName).toList())
//        println("${kpics.LibFile.all().limit(10).joinToString { it.baseName + "\n"}}")
//        println("${kpics.LibFile.find { kpics.AgLibraryFile.extension eq "MP4"}.count()}")
        println("${LibFile.all().limit(3).toList()}")
    }
}
//    id_local
//    id_global
//    baseName
//    errorMessage
//    errorTime
//    extension
//    externalModTime
//    folder
//    idx_filename
//    importHash
//    lc_idx_filename
//    lc_idx_filenameExtension
//    md5
//    modTime
//    originalFilename
//    sidecarExtensions
//    index_AgLibraryFile_folder
//    index_AgLibraryFile_importHash
//    index_AgLibraryFile_nameAndFolder
//    sqlite_autoindex_AgLibraryFile_1
//    trigger_AgDNGProxyInfo_fileDeleted
//    trigger_AgDNGProxyInfo_fileInserted
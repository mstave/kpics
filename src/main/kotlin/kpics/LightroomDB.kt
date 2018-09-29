package kpics

import mu.KLogger
import mu.KotlinLogging
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.OrOp
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.booleanLiteral
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteDataSource
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.util.*

class LightroomDB(private val fName: String?) : AbstractPicCollection() {
    private val logger: KLogger = KotlinLogging.logger {}

    private var libFile = LibFile
    private val ds = SQLiteDataSource()
    override val baseStr
        get() = fName
    private lateinit var db: Database

    init {
        ds.url = "jdbc:sqlite:$fName"
        logger.debug("Creating a LDB for $fName")
        require(File(fName).isFile)
//            db = Database.connect(ds)
//            TransactionManager.manager.defaultIsolationLevel =
//                    Connection.TRANSACTION_SERIALIZABLE
    }

    fun getAll(): ArrayList<LibFile> {
        lateinit var result: ArrayList<LibFile>
        xact {
            result = ArrayList(libFile.all().toList())
        }
        return result
    }

    override val paths: TreeSet<Path>
        get() {
            lateinit var result: TreeSet<Path>
            xact {
                result = TreeSet(getAll().map { it.toPath() })
            }
            return result
        }
    override val relativePaths: TreeSet<String?>
        get() {
            lateinit var result: TreeSet<String?>
            xact {
                result = TreeSet(getAll().map { it.toPathFromRoot() }.toSet())
            }
            return result
        }

    override fun getFullPath(relPath: String): String? {
        var fPath: String? = null
        xact {
            val base = relPath.substringBeforeLast(".")
            val ext = relPath.substringAfterLast(".")
            val findRes = libFile.find {
                ((AgLibraryFile.baseName eq base) and (AgLibraryFile.extension eq ext))
            }
            if (!findRes.empty()) {
                fPath = findRes.first().toString()
            }
        }
        return fPath
    }

    fun containsRelPath(relPath: Path): Boolean {
        var count = 0
        xact {
            var extension = ""
            val i = relPath.fileName.toString().lastIndexOf('.')
            if (i >= 0) {
                extension = relPath.fileName.toString().substring(i + 1)
            }
            val baseName = relPath.fileName.toString().substring(0, i)
            count = libFile.find {
                (AgLibraryFile.baseName eq baseName).and(
                        OrOp(booleanLiteral(!baseName.contains(".")), AgLibraryFile.extension eq extension))
            }.count()
        }
        return count > 0
    }

    override val count: Int
        get() = {
            var count = 0
            xact {
                count = libFile.all().count()
            }
            count
        }()

    fun xact(body: () -> Unit) {
        db = Database.connect(ds)
        TransactionManager.manager.defaultIsolationLevel =
                Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            body()
        }
    }
} // Lightroom DB Objects

object AgLibraryRootFolder : IntIdTable(columnName = "id_local") {
    val id_global = varchar("id_global", 32)
    val absolutePath = varchar("absolutePath", 256)
    val name = varchar("name", 256)
    val relativePathFromCatalog = varchar("relativePathFromCatalog", 256)
}

object AgLibraryFolder : IntIdTable(columnName = "id_local") {
    val id_global = varchar("id_global", 32)
    val rootFolder = reference("rootFolder", AgLibraryRootFolder)
    val pathFromRoot = varchar("pathFromRoot", 256)
}

object AgLibraryFile : IntIdTable(columnName = "id_local") {
    val id_global = varchar("id_global", 32)
    val baseName = varchar("baseName", 128)
    val extension = varchar("extension", 128)
    val folder = reference("folder", AgLibraryFolder)
    val modTime = long("modTime")
    val externalModTime = long("externalModTime")
}

class LibFolder(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LibFolder>(AgLibraryFolder)

    val path by LibFile referrersOn AgLibraryFile.folder
    private var rootFolder by LibRootFolder referencedOn AgLibraryFolder.rootFolder
    var pathFromRoot by AgLibraryFolder.pathFromRoot
    override fun toString(): String {
        //return rootFolder.toString()
        return "$rootFolder$pathFromRoot"
    }
}

class LibRootFolder(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LibRootFolder>(AgLibraryRootFolder)

    private var absolutePath by AgLibraryRootFolder.absolutePath
    val rootFolder by LibFolder referrersOn AgLibraryFolder.rootFolder
    override fun toString(): String {
        return absolutePath
    }
}

open class LibFile(id: EntityID<Int>) : IntEntity(id), Comparable<LibFile> {
    companion object : IntEntityClass<LibFile>(AgLibraryFile)

    private var folder by LibFolder referencedOn AgLibraryFile.folder
    var baseName by AgLibraryFile.baseName
    var extension by AgLibraryFile.extension
    private var modTime by AgLibraryFile.modTime
    private var externalModTime by AgLibraryFile.externalModTime
    val modDateTime: java.util.Date
        get() = fromCrazyAppleDate(modTime)
    val externalModDateTime: java.util.Date
        get() = fromCrazyAppleDate(externalModTime)

    private fun fromCrazyAppleDate(crazy: Long): java.util.Date {
        val crazyAppleDataConstant = 978307200L
        return java.util.Date(1000 * (crazy + crazyAppleDataConstant))
    }

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
        return transaction {
            return@transaction "$folder$baseName.$extension"
        }
//        return "$folder$baseName.$extension @ ${modDateTime}"
    }
}
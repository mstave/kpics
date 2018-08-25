package kpics

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Slf4jSqlLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection

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
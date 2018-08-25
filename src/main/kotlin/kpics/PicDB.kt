package kpics

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteDataSource
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.util.*

class PicDB(private val fName: String?) : AbstractPicCollection() {
    private var libFile = LibFile
    private val ds = SQLiteDataSource()
    override val baseStr
        get() = fName
    private var db: Database

    init {
        ds.url = "jdbc:sqlite:$fName"
        require(File(fName).isFile)
        db = Database.connect(ds)
        TransactionManager.manager.defaultIsolationLevel =
                Connection.TRANSACTION_SERIALIZABLE
    }

    fun getAll(): ArrayList<LibFile>? {
        TransactionManager.manager.defaultIsolationLevel =
                Connection.TRANSACTION_SERIALIZABLE
        return transaction(db) {
            return@transaction ArrayList(libFile.all().toList())
        }
    }

    override val paths: TreeSet<Path>
        get() {
            TransactionManager.manager.defaultIsolationLevel =
                    Connection.TRANSACTION_SERIALIZABLE
            return transaction(db) {
                return@transaction TreeSet(getAll()?.map { it.toPath() })
            }
        }
    override val relativePaths: TreeSet<String?>
        get() {
            TransactionManager.manager.defaultIsolationLevel =
                    Connection.TRANSACTION_SERIALIZABLE
            return transaction(db) {
                return@transaction TreeSet(getAll()?.map { it.toPathFromRoot() }?.toSet())
            }
        }

    override fun getFullPath(relPath: String): String? {
        TransactionManager.manager.defaultIsolationLevel =
                Connection.TRANSACTION_SERIALIZABLE
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

    fun containsRelPath(relPath: Path): Boolean {
        var count = 0
        TransactionManager.manager.defaultIsolationLevel =
                Connection.TRANSACTION_SERIALIZABLE
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
            TransactionManager.manager.defaultIsolationLevel =
                    Connection.TRANSACTION_SERIALIZABLE
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
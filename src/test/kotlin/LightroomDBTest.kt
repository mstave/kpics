

import kpics.LightroomDB
import kpics.LocalPicFiles
import mu.KotlinLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tornadofx.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

internal class LightroomDBTest {
    private val logger = KotlinLogging.logger {}

    companion object {
        const val testPicsPath = "/Users/mstave/Dropbox/Photos/testPicsPath/"
        val dropbox : String
        get() {
            var guess = "/Users/mstave/Dropbox"
            if (!File(guess).exists()) {
                guess = "/mnt/g/Dropbox"
            }
            if (!File(guess).exists()) {
                guess = "g:\\Dropbox"
            }
            return guess
        }
        fun getTestPics(): LightroomDB {
            val filename = java.io.File("$dropbox/pic.db").absolutePath
            return LightroomDB(filename)
        }
    }

    // TODO replace weak-ass println tests with asserts with testdata
    @Test
    fun relPaths() {
        val db = getTestPics()
        print(db.relativePaths.first())

    }
    @Test
    fun basePathStr() {
        println(getTestPics().baseStr)
    }

    @Test
    fun testlocDBRel() {
        val localdbFile = java.io.File("$dropbox/pic.db").absolutePath
        val db = LightroomDB(localdbFile)
        print(db.relativePaths.observable())
        print(db.count)
        assertTrue(db.relativePaths.size > 0)

    }

    @Test
    fun totalPics() {
    }

    @Test
    fun transact() {
    }

    @org.junit.jupiter.api.Test
    fun bogusDBIsBogus() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            val bogus = LightroomDB("bogus.db")
            println(bogus)
        }
        println(exception)
    }

    @org.junit.jupiter.api.Test
    fun getNewPicDB() {
        assertNotNull(getTestPics())
    }

    @org.junit.jupiter.api.Test
    fun hasRows() {
        val db = getTestPics()
        db.xact {
            assertTrue(LightroomDB.LibFile.find { LightroomDB.AgLibraryFile.extension eq "MP4" }.count() > 0)
        }
    }

    @org.junit.jupiter.api.Test
    fun getModTime() {
    }

    @Test
    fun example() {
        val db = getTestPics()
        db.xact {
            val folderQ = LightroomDB.LibFolder.wrapRows(
                    LightroomDB.AgLibraryFolder.innerJoin(LightroomDB.AgLibraryFile).select { LightroomDB.AgLibraryFile.extension eq "MP4" }
                                                        ).toList().distinct()
            logger.debug("Folder: " + folderQ.toString())
            folderQ.forEach { logger.debug(it.toString()) }
            val fileQ = LightroomDB.LibFile.wrapRows(
                    LightroomDB.AgLibraryFolder.innerJoin(LightroomDB.AgLibraryFile).select { LightroomDB.AgLibraryFile.extension eq "MP4" }
                                                    ).toList()
            logger.debug("File: " + fileQ.toString())
        }
    }

    @Test
    fun testGetRel() {
        val it: Path = getTestPics().paths.last()
        val localF = LocalPicFiles("$dropbox/Photos/pics")
        val pathVal = Paths.get(
                it.fileName.toString().replace(
                        (localF.baseStr), ""))
        println(pathVal)
        println(getTestPics().paths.last())
    }
    @Test
    fun containsRelPathTest() {
        val f: LightroomDB = getTestPics()
        println(f.containsRelPath(Paths.get("1980/February/0728101523-00.jpg")))

    }

    @Test
    fun testchecksum() {
//        org.junit.jupiter.api.fail ("implement me")
//        org.junit.jupiter.api.Assumptions.assumeTrue(false)
    }
    @Test
    fun pathTest() {
        val p = Paths.get("foo/bar/baz.txt")
        println(p.fileName)
        println(p.parent)
        println(p.root)
        println(p.normalize())
        println(p.subpath(0, 1))
        println(p - Paths.get("foo"))
    }

    @Test
    fun testContainsName() {
        val f: LightroomDB = getTestPics()
        print(f.containsName("DSC00665.ARW"))
    }

    @Test
    fun testGetFullPath() {
        val f: LightroomDB = getTestPics()
//        print(f.getFullPath("DSC00665.ARW"))
        val first = f.getAll()!!.first()
        val firstName = first.baseName + "." +
                        first.extension
        print(firstName)
        val ffile = File(f.getFullPath(firstName))
        print(ffile.isFile)
    }

    @Test
    fun testContainsName2() {
        val f: LightroomDB = getTestPics()
        println(f.containsName("BILLROCK.JPG"))
        println(f.containsName("WISESHOW.JPG"))
    }

    @Test
    fun testContainsName3() {
        val f: LightroomDB = getTestPics()
        println(f.containsName("WISESHOW.JPG"))
    }

    @Test
    fun testPicDBCount() {
        val f = getTestPics()
        Assertions.assertNotEquals(0, f.count)
    }

    @Test
    fun testGetAll() {
        val f = getTestPics()
        val t = f.getAll()
        println(t?.count())
        Assertions.assertNotEquals(0, f.getAll()?.count())
        transaction {
            Assertions.assertNotEquals(0, f.getAll()?.count())
        }
    }

    @Test
    fun testToPathFromRoot() {
        val f = getTestPics()
        val first = f.getAll()?.last()
        if (first != null) {
            println(first.toPathFromRoot())
        }

    }

}
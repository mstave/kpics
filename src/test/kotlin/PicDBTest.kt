

import kpics.*
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

internal class PicDBTest {
    private val logger = KotlinLogging.logger {}

    companion object {
        const val testPicsPath = "/Users/mstave/Dropbox/Photos/testPicsPath/"
        fun getTestPics(): PicDB {
            val dropbox = "/Users/mstave/Dropbox"
            val filename = java.io.File("$dropbox/pic.db").absolutePath
            return PicDB(filename)
        }
    }

    @Test
    fun relPaths() {
        val db = getTestPics()
        print(db.relativePathSet)

    }

    @Test
    fun testlocDBRel() {
        val dropbox = "/Users/mstave/Dropbox"
        val localdbFile = java.io.File("$dropbox/pic.db").absolutePath
        val db = PicDB(localdbFile)
        print(db.relativePathSet?.observable())
        print(db.getCount())
        assertTrue(db.relativePathSet!!.size > 0)

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
            val bogus = PicDB("bogus.db")
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
            assertTrue(LibFile.find { AgLibraryFile.extension eq "MP4" }.count() > 0)
        }
    }

    @org.junit.jupiter.api.Test
    fun getModTime() {
    }

    @Test
    fun example() {
        val db = getTestPics()
        db.xact {
            val folderQ = LibFolder.wrapRows(
                    AgLibraryFolder.innerJoin(AgLibraryFile).select { AgLibraryFile.extension eq "MP4" }
            ).toList().distinct()
            logger.debug("Folder: " + folderQ.toString())
            folderQ.forEach { logger.debug(it.toString()) }
            val fileQ = LibFile.wrapRows(
                    AgLibraryFolder.innerJoin(AgLibraryFile).select { AgLibraryFile.extension eq "MP4" }
            ).toList()
            logger.debug("File: " + fileQ.toString())
        }
    }

    fun goal() {

    }

    @Test
    fun testGetRel() {
        val it: Path = getTestPics().getPaths().last()
        val localF = PicFiles("/Users/mstave/Dropbox/Photos/pics")
        val pathVal = Paths.get(
                it.fileName.toString().replace(
                        (localF.getBaseStr()), ""))
        println(pathVal)
        println(getTestPics().getPaths().last())
    }
    @Test
    fun containsRelPathTest() {
        val f: PicDB = getTestPics()
        println(f.containsRelPath(Paths.get("1980/February/0728101523-00.jpg")))

    }

    @Test
    fun testchecksum() {
//        org.junit.jupiter.api.fail ("implement me")
        org.junit.jupiter.api.Assumptions.assumeTrue(false)
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
        val f: PicDB = getTestPics()
        print(f.containsName("DSC00665.ARW"))
    }

    @Test
    fun testGetFullPath() {
        val f: PicDB = getTestPics()
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
        val f: PicDB = getTestPics()
        println(f.containsName("BILLROCK.JPG"))
        println(f.containsName("WISESHOW.JPG"))
    }

    @Test
    fun testContainsName3() {
        val f: PicDB = getTestPics()
        println(f.containsName("WISESHOW.JPG"))
    }

    @Test
    fun testPicDBCount() {
        val f = getTestPics()
        Assertions.assertNotEquals(0, f.getCount())
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
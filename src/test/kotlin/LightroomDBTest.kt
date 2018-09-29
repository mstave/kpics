import kotlinx.coroutines.*
import kpics.*
import mu.KotlinLogging
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.jetbrains.exposed.sql.select
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
internal class LightroomDBTest {
    private val logger = KotlinLogging.logger {}
    private lateinit var sampleDB: LightroomDB
    private val sampleDBPath = System.getProperty("user.dir") +
                               File.separator + "testdata" + File.separator + "testpics" +
                               File.separator + "testpics.lrcat"

    companion object { // so KPicsTest can aggregate
        private val dropbox: String
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

        fun getCustomTestPics(): LightroomDB {
            // works with my personal pics
            val filename = java.io.File("$dropbox/pic.db").absolutePath
            Assumptions.assumeTrue(File(filename).exists())
            return LightroomDB(filename)
        }
    }

    @BeforeAll
    fun setupSample() {
        Configurator.setLevel(LocalPicFiles::class.simpleName, Level.DEBUG)
        Configurator.setLevel("Exposed", Level.INFO) // JDBC stuff can be chatty

        sampleDB = LightroomDB(sampleDBPath)
    }

    @Test
    fun relPaths() {
        assertEquals("2016-02-23 07.57.33.png", sampleDB.relativePaths.first())
    }

    @Test
    fun sampleDBconcurrency() {
        val first = GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, {
            sampleDB.xact {
                logger.info(sampleDB.getAll().first().toString())
            }
        })
        val second = GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, {
            sampleDB.getAll()
        })
        runBlocking {
            first.await()
            second.await()
        }
    }

    @Test
    fun testConcurrency() {
        val first = GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, {
            sampleDB.xact {
                logger.info(sampleDB.getAll().first().toString())
            }
        })
        val second = GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, {
            val f: LightroomDB = getCustomTestPics()
            f.getAll()
        })
        runBlocking {
            first.await()
            second.await()
        }
    }

    @Test
    fun basePathStr() {
        assertEquals(sampleDBPath, sampleDB.baseStr)
    }

    @Test
    fun sampleHasFiles() {
        assertTrue(sampleDB.relativePaths.size > 0)
    }

    @Test
    fun transact() {
        sampleDB.xact {
        }
    }

    @Test
    fun bogusDBIsBogus() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            val bogus = LightroomDB("bogus.db")
            println(bogus)
        }
        println(exception)
    }

    @Test
    fun getNewPicDB() {
        assertNotNull(getCustomTestPics())
    }

    @Test
    fun hasRows() {
        val db = getCustomTestPics()
        db.xact {
            assertTrue(LibFile.find { AgLibraryFile.extension eq "MP4" }.count() > 0)
        }
    }

    @org.junit.jupiter.api.Test
    fun getModTime() {
    }

    @Test
    fun example() {
        val db = getCustomTestPics()
        db.xact {
            val folderQ = LibFolder.wrapRows(
                    AgLibraryFolder.innerJoin(
                            AgLibraryFile).select { AgLibraryFile.extension eq "MP4" }
                                            ).toList().distinct()
            logger.debug("Folder: " + folderQ.toString())
            folderQ.forEach { logger.debug(it.toString()) }
            val fileQ = LibFile.wrapRows(
                    AgLibraryFolder.innerJoin(
                            AgLibraryFile).select { AgLibraryFile.extension eq "MP4" }
                                        ).toList()
            logger.debug("File: " + fileQ.toString())
        }
    }

    @Test
    fun testGetRel() {
        val it: Path = getCustomTestPics().paths.last()
        val localF = LocalPicFiles("$dropbox/Photos/pics")
        val pathVal = Paths.get(
                it.fileName.toString().replace(
                        (localF.baseStr), ""))
        println(pathVal)
        println(getCustomTestPics().paths.last())
    }

    @Test
    fun containsRelPathTest() {
        val f: LightroomDB = getCustomTestPics()
        println(f.containsRelPath(Paths.get("1980/February/0728101523-00.jpg")))
    }

    @Test
    fun testDate() {
        val f: LightroomDB = getCustomTestPics()
        val a = f.getAll().first().externalModDateTime
        val b = f.getAll().first().modDateTime
        logger.info("$a $b")
    }

    @Test
    fun testContainsName() {
        val f: LightroomDB = getCustomTestPics()
        print(f.containsName("DSC00665.ARW"))
    }

    @Test
    fun testGetFullPath() {
        val f: LightroomDB = getCustomTestPics()
//        print(f.getFullPath("DSC00665.ARW"))
        val first = f.getAll().first()
        val firstName = first.baseName + "." +
                        first.extension
        print(firstName)
        val ffile = File(f.getFullPath(firstName))
        print(ffile.isFile)
    }

    @Test
    fun testContainsNameCustom() {
        val f: LightroomDB = getCustomTestPics()
        assertTrue(f.containsName("Lao.jpg"))
        assertTrue(f.containsName("wiseshow.jpg"))
    }

    @Test
    fun containsName() {
        logger.info(sampleDB.getAll().toString())
        assertTrue(sampleDB.containsName("312.png"))
        assertTrue(sampleDB.containsName("screen.jpg"))
    }

    @Test
    fun testPicDBCount() {
        val f = getCustomTestPics()
        Assertions.assertNotEquals(0, f.count)
    }

    @Test
    fun testSampleCount() {
        assertEquals(4, sampleDB.getAll().size)
        assertEquals(4, sampleDB.count)
    }

    @Test
    fun testGetAll() {
        val f = getCustomTestPics()
        Assertions.assertNotEquals(0, f.getAll().count())
    }

    @Test
    fun testFirstNotLast() {
        assertNotEquals(sampleDB.getAll().first(), sampleDB.getAll().last())
    }

    @Test
    fun testToPathFromRoot() {
        val f = getCustomTestPics()
        val last = f.getAll().last()
        assertEquals("stone/2018/2018-01-03/DSC00693.ARW",
                     last.toPathFromRoot())
    }
}
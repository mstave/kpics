import kotlinx.coroutines.*
import kpics.LocalPicFiles
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

internal class LocalPicFilesTest {
    private val logger = KotlinLogging.logger {}

    companion object {
        private val testPicsPath = System.getProperty("user.dir") +
                                   File.separator + "testdata" + File.separator + "pics"
        private val testPF =  LocalPicFiles(testPicsPath)
        fun getTestPics(): LocalPicFiles {
            return testPF
        }
    }

    @Test
    fun testGetDirsWithAllDupes() {
        val theDupes = getTestPics().getDirsWithAllDupes()

//        println("${testPicsPath + File.separator}rootdupe")
        assertNotNull(theDupes)
        if (theDupes.size == 0) {
            println(theDupes)
            println(getTestPics().getDirStats())
        }
        assertEquals(1, theDupes.size)
        assertEquals("${testPicsPath + File.separator}rootdupe", theDupes[0])
    }

    @Test
    fun testDupes() {
        val dupes = getTestPics().dupeFiles
        assertNotNull(dupes)
        assertEquals(2, dupes!!.size)
    }
    @Test
    fun myTestDupes() {
        var myPath = "/Users/mstave/Dropbox/photos/pics"
        if (System.getProperty("os.name").startsWith("Windows")) {
            myPath = "f:/Dropbox.old/photos/pics"

        }
        assumeTrue(File(myPath).exists())
        val dupes = LocalPicFiles(myPath).getDupes()
        assertNotNull(dupes)
        assertEquals(1369, dupes!!.size)
//        assertEquals(2, dupes!!.size)
    }

    @Test
    fun testGetProviders() {
        java.security.Security.getProviders().forEach {
            println(it.name)
            it.services.forEach { ser ->
                println("\t " + ser.algorithm)
            }
        }
    }

   @Test
    fun testChecksumUnique() {
        val pics = getTestPics()
        val dupe = pics.getDupes()
        assertNotNull(dupe)
        assertTrue(dupe!!.size > 0)
    }

    @Test
    fun testFiles() {
        val f = getTestPics()
        logger.info(f.basePath.toString())
        f.printFiles()
    }

    @Test
    fun testGetCount() {
        val f = getTestPics()
        assertNotEquals(
                0,
                f.count,
                "no testPicsPath found"
                       )
    }

    @Test
    fun testDupeMemoize() {
        val pf = getTestPics()
        val d1 = GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, { pf.getDupes() })
        val d2 = GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, { pf.getDupes() })
        runBlocking {
            println(d1.await()?.size)
            println(d2.await()?.size)
            assertNotNull(d1)
            assertNotNull(d2)
            assertTrue(d1.await()?.size!! > 0)
        }
    }

    @Test
    fun testForEachDupe() {
        getTestPics().getDupes()?.forEach {
            println(it)
        }
    }

    @Test
    fun testGetDupeFileList() {
        assertEquals(6, getTestPics().getDupeFileList()?.size)
    }

    @Test
    fun testGetDupesForFile() {
//        val gold = "/Users/mstave/Dropbox/Photos/pics/picnic.jpg"
        val gold = "/Users/mstave/Dropbox/Photos/pics/000adel332.jpg"
        println(getTestPics().getDupesForFile(gold))
    }

    @Test
    fun testGetDirStats() {
        val res = getTestPics().getDirStats()
        println(res.toString())
    }

    @Test
    fun testRootForDupes() {
        val tp = getTestPics()
        tp.getDupesForDir(tp.basePath.toString()).forEach {
            println(it)
        }
    }

    //    @Test
    fun printDupesByDir() {
        getTestPics().getDirStats().forEach {
            println(it)
        }
    }

    @Test
    fun testWin() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))

    }

    @Test
    fun testRelativePath() {
        val f = getTestPics()
        assertNotNull(f.relativePaths)
        val firstName: String = f.relativePaths.last()!!
        assertFalse(firstName.contains(f.baseStr))
    }

    @Test
    fun testGetFullPath() {
        val f = getTestPics()
        val firstName: String = f.relativePaths.last()!!
        val full = f.getFullPath(firstName)
        assertEquals(f.baseStr + firstName, full)
        val ffile = File(f.getFullPath(firstName))
        assertTrue(ffile.isFile)
        val neg = f.getFullPath("BOGUS")
        assertEquals("", neg)
    }
}
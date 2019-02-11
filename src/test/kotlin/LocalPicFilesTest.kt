
import kotlinx.coroutines.*
import kpics.LocalPicFiles
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

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
        if (theDupes.isEmpty()) {
            println(theDupes)
            println(getTestPics().getDupeDirStats())
        }
        assertEquals(2, theDupes.size)
        assertTrue(theDupes.contains("${testPicsPath + File.separator}rootdupe"))
    }

    @Test
    fun testDupes() {
        val dupes = getTestPics().dupeFileSets
        assertNotNull(dupes)
        assertEquals(4, dupes.size)
    }
    @Test
    fun myTestDupes() {
        var myPath = "/Users/mstave/Dropbox/photos/pics"
        if (System.getProperty("os.name").startsWith("Windows")) {
            myPath = "f:/Dropbox.old/photos/pics"

        }
        assumeTrue(File(myPath).exists())
        val dupes = LocalPicFiles(myPath).dupeFileSets
        assertNotNull(dupes)
        assertEquals(1371, dupes.size)
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
        val dupe = pics.dupeFileSets
        assertNotNull(dupe)
        assertTrue(dupe.isNotEmpty())
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
        val d1 = GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, { pf.dupeFileSets })
        val d2 = GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, { pf.dupeFileSets })
        runBlocking {
            println(d1.await().size)
            println(d2.await().size)
            assertNotNull(d1)
            assertNotNull(d2)
            assertTrue(d1.await().size > 0)
        }
    }

    @Test
    fun testForEachDupe() {
        getTestPics().dupeFileSets.forEach {
            println(it)
        }
    }

    @Test
    fun testGetDupeFileList() {
        assertEquals(10, getTestPics().getDupeFileList()?.size)
    }

    @Test
    fun testGetDupesForFile() {
//        val gold = "/Users/mstave/Dropbox/Photos/pics/picnic.jpg"
        val gold = "/Users/mstave/Dropbox/Photos/pics/000adel332.jpg"
        println(getTestPics().getDupesForFile(gold))
    }

    @Test
    fun testGetDirStats() {
        val res = getTestPics().getDupeDirStats()
        println(res.toString())
    }

    @Test
    fun testRootForDupes() {
        val tp = getTestPics()
        tp.getDupesInDir(tp.basePath).forEach {
            println(it)
        }
    }
    @Test
    fun testGetDupesForDir() {
        val testPics = getTestPics()
        val here =testPicsPath + File.separator+ "onlydupedhere"
        println(here)
        val onlyHere = testPics.getDupesInDir(Paths.get(here))
        assertEquals(4, onlyHere.size)
        println(onlyHere)
//        /Users/mstave/Dropbox/dev/kpics/testdata/pics/onlydupedhere
    }

    @Test
    fun testDirsWithAllDupes() {
        val testPics = getTestPics()
        val dirsWithAllDupes: Set<String> = testPics.getDirsWithAllDupes()
        assertNotNull(dirsWithAllDupes)
        println(dirsWithAllDupes)
    }
    //    @Test
    fun printDupesByDir() {
        getTestPics().getDupeDirStats().forEach {
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
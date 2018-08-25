import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import kpics.PicFiles
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

internal class PicFilesTest {
    private val logger = KotlinLogging.logger {}

    companion object {
        //        const val testPicsPath = "/Users/mstave/Dropbox/Photos/pics/"
        private val testPicsPath = System.getProperty("user.dir") +
                                   File.separator + "testdata/pics"

        fun getTestPics(): PicFiles {
            return PicFiles(testPicsPath)
        }
    }

    @Test
    fun testGetDirsWithAllDupes() {
        val test = getTestPics().getDirsWithAllDupes()

        assertNotNull(test)
        assertEquals(1, test.size)
        assertEquals("${testPicsPath + File.separator}rootdupe", test[0])
    }

    @Test
    fun testDupes() {
        val dupes = getTestPics().dupeFiles
        assertNotNull(dupes)
        assertEquals(2, dupes!!.size)
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

    //    import java.util.zip.CRC32
//
//    fun main(args: Array<String>) {
//        val text = "The quick brown fox jumps over the lazy dog"
//        val crc = CRC32()
//        with (crc) {
//            update(text.toByteArray())
//            println("The CRC-32 checksum of '$text' = ${"%x".format(value)}")
//        }
//    }
    @Test
    fun testMD5sUnique() {
        val testPicsPathStr = "/Users/mstave/Dropbox/Photos/pics/"
//        val testPicsPathStr = "/Users/mstave/Dropbox/Photos/pics/"
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
        val d1 = async { pf.getDupes() }
        val d2 = async { pf.getDupes() }
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
        println(getTestPics().getDupeFileList())
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
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val p = PicFiles("g:\\Dropbox\\Photos\\pics")
        println(p.getRelativePaths().first())
        println(p.baseStr + File.separator)
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

import kpics.PicFiles
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.ConcurrentSkipListSet

internal class PicFilesTest {
    private val logger = KotlinLogging.logger {}

    companion object {
        const val testPicsPath = "/Users/mstave/Dropbox/Photos/pics/"
        fun getTestPics(): PicFiles {
            return PicFiles(testPicsPath)
        }
    }

    @Test
    fun HashTest() {
        val h = ConcurrentSkipListSet<String>()

        println(h.size)
        println(h.add("Foo"))
        println(h.size)
        println(h.add("Foo"))
        println(h.size)
    }

    @Test
    fun testGetProviders() {
        java.security.Security.getProviders().forEach {
            println(it.name)
            it.services.forEach() { ser->
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
//        val testPicsPath = "/Users/mstave/Dropbox/Photos/pics/uk/"
        val testPicsPathStr = "/Users/mstave/Dropbox/Photos/pics/"
        val pics = PicFiles(testPicsPathStr)
        val dupe = pics.getDupes()
        println(dupe.size)
        Assertions.assertTrue(dupe.size > 0)
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
        Assertions.assertNotEquals(
                0,
                f.getCount(),
                "no testPicsPath found"
                                  )
    }

    @Test
    fun testWin() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val p = PicFiles("g:\\Dropbox\\Photos\\pics")
        println(p.getRelativePaths().first())
        println(p.getBaseStr() + File.separator)
    }

    @Test
    fun testGetFullPath() {
        val f = getTestPics()
//        print(f.getFullPath("DSC00665.ARW"))
        val firstName = f.getRelativePaths().last()
        println(firstName)
        val ffile = File(f.getFullPath(firstName))
        print(ffile.isFile)
    }
}
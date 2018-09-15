package kgui.view

import javafx.stage.Stage
import kgui.app.KGuiPics
import mu.KotlinLogging
import org.junit.jupiter.api.*
import org.testfx.api.FxToolkit
import tornadofx.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DupeViewTest {
    private val logger = KotlinLogging.logger {}
    var app = KGuiPics()
    @BeforeAll
    fun beforeAll() {
        System.setProperty("testfx.setup.timeout", "52500")
        val primaryStage: Stage = FxToolkit.registerPrimaryStage()
        FX.registerApplication(FxToolkit.setupApplication {
            app
        }, primaryStage)
        try {
            app.start(primaryStage)
        } catch (e: Exception) {
            logger.warn("warning:", e)
        }
        Assertions.assertNotNull(app, "KGuiPics")
        logger.info("onstart Done")
    }

    @AfterAll
    fun stop() {
        logger.info("Stopping")
        app.stop()
    }

    @Test
    fun dummyPass() {
        logger.info("DummyPass called")
    }

    @Test
    fun getRoot() {
        logger.info("             ==== getTestRoot Starts")
        // TODO: break out heavy lifting from the controller to a non-javaFX class for easier testing
        val dv = find<DupeView>()
        val ds = dv.dupeC.dupeStrings
        val dc = find<DupeController>()
        Assertions.assertNotNull(dc)
        var count = 0
        while (!dc.doneSearching.get()) {
            if (count < 90) {
                Thread.sleep(1000)
                count++
            } else {
                logger.warn(" ==== getTestRoot hit timeout")
                break
            }
        }
        Assertions.assertNotNull(ds)
        Assertions.assertNotNull(dc)
        Assertions.assertTrue(dc.dupeStrings.size > 10)
        logger.info("             ==== getTestRoot done")
    }
}

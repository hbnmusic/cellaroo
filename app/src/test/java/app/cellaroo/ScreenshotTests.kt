package app.cellaroo

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.cellaroo.core.DrinkWindow
import app.cellaroo.core.Pairing
import app.cellaroo.core.PairingTag
import app.cellaroo.core.ParsedScore
import app.cellaroo.core.ServingMode
import app.cellaroo.core.Source
import app.cellaroo.core.WineType
import app.cellaroo.data.AddItem
import app.cellaroo.data.BottleDraft
import app.cellaroo.data.WineDraft
import app.cellaroo.data.encodePairings
import app.cellaroo.ui.AppRoot
import app.cellaroo.ui.CellarooTheme
import app.cellaroo.ui.Palette
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with a seeded database and saves PNGs to build/outputs/roborazzi.
 * These are smoke tests: they fail if a screen crashes while composing, and CI publishes the images for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: CellarooApp get() = ApplicationProvider.getApplicationContext()

    private data class Seed(
        val producer: String, val name: String, val vintage: Int?, val region: String, val country: String,
        val type: WineType, val grapes: List<String>, val box: List<Int>, val qty: Int, val price: Double?,
    )

    // Boxes are hand-estimated for samples/costco-cart-6.png so the overlay mapping can be checked by eye.
    private val seeds = listOf(
        Seed("Viu Manent", "La Capilla Single Vineyard Cabernet Sauvignon", 2022, "Valle de Colchagua", "Chile", WineType.RED, listOf("Cabernet Sauvignon"), listOf(52, 200, 440, 410), 2, 15.99),
        Seed("Marchesi di Barolo", "Gavi", null, "Gavi DOCG", "Italy", WineType.WHITE, listOf("Cortese"), listOf(52, 410, 458, 580), 1, 13.49),
        Seed("Belvedere", "Muscadet Sèvre et Maine sur Lie", 2024, "Muscadet Sèvre et Maine", "France", WineType.WHITE, listOf("Melon de Bourgogne"), listOf(55, 575, 502, 750), 1, 9.99),
        Seed("Viu Manent", "ViBo Viñedo Centenario", 2021, "Valle de Colchagua", "Chile", WineType.RED, listOf("Cabernet Sauvignon", "Malbec"), listOf(475, 250, 943, 452), 1, 16.99),
        Seed("Luis R", "Rioja Crianza", 2021, "Rioja DOCa", "Spain", WineType.RED, listOf("Tempranillo"), listOf(472, 452, 920, 603), 3, 11.99),
        Seed("Marqués de Riscal", "Reserva", 2021, "Rioja DOCa", "Spain", WineType.RED, listOf("Tempranillo"), listOf(515, 593, 912, 750), 1, null),
    )

    private var photoName: String? = null
    private var photoW = 1062
    private var photoH = 1633
    private var shelf = 0L

    private fun Seed.draft() = WineDraft(producer, name, region, country, vintage, type, grapes)

    private fun seed() = runBlocking {
        val c = app.container
        shelf = c.repo.ensureDefaultLocation()
        val photo = c.images.importFile(File("../samples/costco-cart-6.png"))
        photoName = photo?.name
        photo?.let { photoW = it.width; photoH = it.height }
        val now = System.currentTimeMillis()
        seeds.forEachIndexed { i, s ->
            val crop = photo?.let { c.images.crop(it.name, s.box, "seed$i") }
            val bottles = List(s.qty) { BottleDraft(shelf, s.price, if (s.price != null) now else null, "Costco", crop, "SCAN") }
            c.repo.addBottles(listOf(AddItem(null, s.draft(), bottles, crop)), "SCAN", "Added ${s.qty} from a photo")
        }
        val year = app.cellaroo.ai.currentYear()
        fun pairings(vararg p: Pair<PairingTag, String>) = encodePairings(p.map { Pairing(it.first, it.second) })
        for (w in c.repo.dao.wines()) {
            val updated = when (w.producer to w.wineName.take(5)) {
                "Viu Manent" to "La Ca" -> w.copy(
                    drinkFromYear = year - 2, peakStartYear = year - 1, peakEndYear = year + 3, drinkUntilYear = year + 6,
                    windowSource = Source.AI_ESTIMATE, servingMode = ServingMode.FOOD, servingSource = Source.AI_ESTIMATE,
                    servingReason = "Firm tannins and dark fruit want protein and fat.", servingTempC = 17, decantMinutes = 30,
                    pairingsJson = pairings(PairingTag.NEW_AMERICAN to "Grilled ribeye", PairingTag.MIDDLE_EASTERN to "Lamb kofta", PairingTag.VIETNAMESE to "Bò lúc lắc"),
                    pairingSource = Source.AI_ESTIMATE, marketPriceUsd = 21.99, marketPriceAsOf = now, marketPriceSource = Source.PASTE,
                    styleSummary = "Cassis, cedar and graphite; medium-plus tannin.",
                )
                "Marchesi di Barolo" to "Gavi" -> w.copy(
                    drinkFromYear = year - 1, drinkUntilYear = year + 1, windowSource = Source.AI_ESTIMATE,
                    servingMode = ServingMode.EITHER, servingSource = Source.AI_ESTIMATE, servingTempC = 9, decantMinutes = 0,
                    pairingsJson = pairings(PairingTag.ITALIAN to "Vitello tonnato", PairingTag.JAPANESE to "Sashimi", PairingTag.VIETNAMESE to "Gỏi cuốn"),
                    pairingSource = Source.AI_ESTIMATE,
                )
                "Belvedere" to "Musca" -> w.copy(
                    drinkFromYear = year - 1, peakStartYear = year - 1, peakEndYear = year, drinkUntilYear = year + 2, windowSource = Source.PASTE,
                    servingMode = ServingMode.EITHER, servingSource = Source.PASTE,
                    pairingsJson = pairings(PairingTag.FRENCH to "Oysters", PairingTag.THAI to "Som tam"), pairingSource = Source.PASTE,
                    marketPriceUsd = 12.0, marketPriceAsOf = now - 400L * 24 * 3600 * 1000, marketPriceSource = Source.MANUAL,
                )
                "Viu Manent" to "ViBo " -> w.copy(
                    drinkFromYear = year - 3, peakStartYear = year - 2, peakEndYear = year + 2, drinkUntilYear = year + 5, windowSource = Source.MANUAL,
                    stickerNote = "93",
                )
                "Luis R" to "Rioja" -> w.copy(drinkFromYear = year + 1, drinkUntilYear = year + 7, windowSource = Source.AI_ESTIMATE)
                else -> w
            }
            c.repo.updateWine(updated)
            if (w.wineName.startsWith("La Ca")) c.repo.addScores(w.id, listOf(ParsedScore("JS", "93", 93.0), ParsedScore("VINOUS", "91-93", 92.0, 91.0, 93.0)), Source.PASTE)
            if (w.wineName.startsWith("ViBo")) c.repo.addScores(w.id, listOf(ParsedScore("WA", "92", 92.0)), Source.MANUAL)
        }
    }

    private fun settle() {
        repeat(8) {
            Thread.sleep(120)
            compose.waitForIdle()
        }
    }

    private fun launch(): MainViewModel {
        seed()
        val vm = MainViewModel(app)
        compose.setContent {
            CellarooTheme { Box(Modifier.fillMaxSize().background(Palette.Background)) { AppRoot(vm) } }
        }
        compose.waitUntil(15_000) { vm.cellar.value.rows.size == seeds.size && vm.cellar.value.rows.any { it.scores.isNotEmpty() } }
        settle()
        return vm
    }

    private fun shoot(name: String) {
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun wineId(vm: MainViewModel, namePrefix: String) = vm.cellar.value.rows.first { it.wine.wineName.startsWith(namePrefix) }.wine.id

    private fun items(reconcile: Boolean): List<ScanItem> = seeds.mapIndexed { i, s ->
        ScanItem(
            id = i + 1, photo = 0, boxes = listOf(s.box), producer = s.producer, wineName = s.name, region = s.region, country = s.country,
            vintage = s.vintage?.toString() ?: "", type = s.type, grapes = s.grapes.joinToString(", "), abv = null, sizeMl = 750,
            sticker = if (s.name.startsWith("ViBo")) "93" else null, legible = true, confidence = if (i == 4) 0.5 else 0.9,
            qty = if (reconcile) 1 else s.qty,
        )
    }

    @Test fun cellarList() {
        launch()
        shoot("01_cellar")
    }

    @Test fun wineDetail() {
        val vm = launch()
        vm.navigate("wine/${wineId(vm, "La Ca")}")
        shoot("02_wine_detail")
    }

    @Test fun tonight() {
        val vm = launch()
        vm.navigate("tonight")
        shoot("03_tonight")
    }

    @Test fun stats() {
        val vm = launch()
        vm.navigate("stats")
        shoot("04_stats")
    }

    @Test fun scanReview() {
        val vm = launch()
        vm.scan.value = ScanSession(ScanKind.ADD, null, listOf(ScanPhoto(photoName ?: "missing.jpg", photoW, photoH)), items(false))
        vm.navigate("scanReview")
        shoot("05_scan_review")
    }

    @Test fun shelfCheck() {
        val vm = launch()
        // The photo "shows" five known bottles (one without a readable vintage) and one new wine; Luis R is absent.
        val seen = items(true).filterNot { it.producer == "Luis R" }.map { if (it.producer == "Marqués de Riscal") it.copy(vintage = "") else it } +
            ScanItem(7, 0, listOf(listOf(472, 452, 920, 603)), "Bodegas Muga", "Reserva", "Rioja DOCa", "Spain", "2020", WineType.RED, "Tempranillo", null, 750, null, true, 0.9)
        vm.scan.value = ScanSession(ScanKind.RECONCILE, shelf, listOf(ScanPhoto(photoName ?: "missing.jpg", photoW, photoH)), seen)
        vm.navigate("reconcileReview")
        shoot("06_shelf_check")
    }

    @Test fun pastePreview() {
        val vm = launch()
        vm.navigate("paste/${wineId(vm, "Reserva")}")
        settle()
        vm.paste.value = PasteParsed(
            text = "…", window = DrinkWindow(2026, 2028, 2034, 2040), priceUsd = 24.99,
            scores = listOf(ParsedScore("JS", "94", 94.0), ParsedScore("CT", "CT91.2", 91.2)),
            notes = "Ripe cherry, vanilla and tobacco; polished tannins.", pairings = listOf(Pairing(PairingTag.SPANISH, "Roast lamb")), usedAi = true,
        )
        shoot("07_paste_preview")
    }

    @Test fun settingsAndScan() {
        val vm = launch()
        vm.navigate("scan")
        shoot("08_scan_tab")
        vm.navigate("settings")
        shoot("09_settings")
    }
}

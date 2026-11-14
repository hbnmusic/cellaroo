package app.cellaroo

import android.app.Application
import androidx.room.Room
import app.cellaroo.ai.AiService
import app.cellaroo.ai.Enricher
import app.cellaroo.ai.GeminiClient
import app.cellaroo.data.Backup
import app.cellaroo.data.CellarDb
import app.cellaroo.data.CellarRepository
import app.cellaroo.data.ImageStore
import app.cellaroo.data.Settings

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    val db: CellarDb = Room.databaseBuilder(app, CellarDb::class.java, "cellaroo.db").build()
    val repo = CellarRepository(db)
    val settings = Settings(app)
    val images = ImageStore(app)
    val ai = AiService(app, settings, GeminiClient())
    val enricher = Enricher(repo, ai)
    val backup = Backup(app, repo, images)
}

class CellarooApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

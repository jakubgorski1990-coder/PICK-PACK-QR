package pl.pickpack.panel

import android.app.Application
import pl.pickpack.panel.data.repository.PickPackRepository

class PickPackApplication : Application() {
    lateinit var repository: PickPackRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = PickPackRepository(this)
    }
}

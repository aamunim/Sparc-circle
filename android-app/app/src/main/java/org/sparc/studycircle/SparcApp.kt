package org.sparc.studycircle

import android.app.Application
import org.sparc.studycircle.core.data.SparcRepository

class SparcApp : Application() {
    lateinit var repository: SparcRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SparcRepository()
    }
}

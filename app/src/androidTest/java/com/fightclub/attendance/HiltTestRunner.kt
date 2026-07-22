package com.fightclub.attendance

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Instrumentation test runner that swaps in [HiltTestApplication] so `androidTest` sources can
 * use `@HiltAndroidTest` and have fakes/mocks injected in place of the real production bindings.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}

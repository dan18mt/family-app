package com.familyhome.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner that replaces the real Application with [HiltTestApplication]
 * so Hilt can inject test dependencies in instrumented tests.
 *
 * Referenced in build.gradle.kts:
 *   testInstrumentationRunner = "com.familyhome.app.HiltTestRunner"
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}

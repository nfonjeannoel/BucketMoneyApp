package com.ivy.wallet.compose

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ivy.design.navigation.Navigation
import com.ivy.wallet.base.*
import com.ivy.wallet.persistence.IvyRoomDatabase
import com.ivy.wallet.persistence.SharedPrefs
import com.ivy.wallet.session.IvySession
import com.ivy.wallet.ui.IvyActivity
import com.ivy.wallet.ui.IvyWalletCtx
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject


@HiltAndroidTest
abstract class IvyComposeTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<IvyActivity>()
    // use createAndroidComposeRule<YourActivity>() if you need access to an activity

    private var idlingResource: IdlingResource? = null

    @Inject
    lateinit var ivyContext: IvyWalletCtx

    @Inject
    lateinit var navigation: Navigation

    @Inject
    lateinit var ivyRoomDatabase: IvyRoomDatabase

    @Inject
    lateinit var ivySession: IvySession

    @Before
    fun setUp() {
        setupTestIdling()
        setupHiltDI()

        TestingContext.inTest = true
    }

    private fun setupTestIdling() {
        TestIdlingResource.reset()
        idlingResource = TestIdlingResource.idlingResource
        composeTestRule.registerIdlingResource(idlingResource!!)
    }

    private fun setupHiltDI() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context(), config)
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        resetTestIdling()

        TestingContext.inTest = false

        resetApp()
    }

    private fun resetTestIdling() {
        idlingResource?.let {
            composeTestRule.unregisterIdlingResource(it)
        }
    }

    private fun resetApp() {
        clearSharedPrefs()
        resetDatabase()
        resetIvyContext()
        ivySession.logout()
    }

    private fun clearSharedPrefs() {
        SharedPrefs(context()).removeAll()
    }

    private fun resetDatabase() {
        ivyRoomDatabase.reset()
    }

    private fun resetIvyContext() {
        ivyContext.reset()
        navigation.reset()
    }

    private fun context(): Context {
        return InstrumentationRegistry.getInstrumentation().targetContext
    }

    protected fun testWithRetry(
        attempt: Int = 0,
        maxAttempts: Int = 3,
        firstFailure: Throwable? = null,
        test: () -> Unit
    ) {
        try {
            test()
        } catch (e: Throwable) {
            if (attempt < maxAttempts) {
                //reset state && retry test
                resetApp()

                composeTestRule.waitMillis(300) //wait for resetting app to finish
                TestIdlingResource.reset()

                //Restart IvyActivity
                composeTestRule.activityRule.scenario.recreate()

                composeTestRule.waitMillis(300) //wait for activity to start

                testWithRetry(
                    attempt = attempt + 1,
                    maxAttempts = maxAttempts,
                    firstFailure = if (attempt == 0) e else firstFailure,
                    test = test
                )
            } else {
                //propagate exception
                throw firstFailure ?: e
            }
        }
    }
}

fun ComposeTestRule.waitSeconds(secondsToWait: Long) {
    val secondsStart = timeNowUTC().toEpochSeconds()
    this.waitUntil(timeoutMillis = (secondsToWait + 5) * 1000) {
        secondsStart - timeNowUTC().toEpochSeconds() < -secondsToWait
    }
}

fun ComposeTestRule.waitMillis(waitMs: Long) {
    val startMs = timeNowUTC().toEpochMilli()
    this.waitUntil(timeoutMillis = waitMs + 5000) {
        startMs - timeNowUTC().toEpochMilli() < -waitMs
    }
}

fun SemanticsNodeInteraction.performClickWithRetry(
    composeTestRule: ComposeTestRule
) {
    composeTestRule.clickWithRetry(
        node = this,
        maxRetries = 3
    )
}

fun ComposeTestRule.clickWithRetry(
    node: SemanticsNodeInteraction,
    retryAttempt: Int = 0,
    maxRetries: Int = 15,
    waitBetweenRetriesMs: Long = 100,
) {
    try {
        node.assertExists()
            .performClick()
    } catch (e: AssertionError) {
        if (retryAttempt < maxRetries) {
            waitMillis(waitBetweenRetriesMs)

            clickWithRetry(
                node = node,
                retryAttempt = retryAttempt + 1,
                maxRetries = maxRetries,
                waitBetweenRetriesMs = waitBetweenRetriesMs
            )
        }
    }
}
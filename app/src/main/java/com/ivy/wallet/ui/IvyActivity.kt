package com.ivy.wallet.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import com.ivy.design.api.IvyUI
import com.ivy.design.api.NavigationRoot
import com.ivy.design.navigation.Navigation
import com.ivy.design.navigation.Screen
import com.ivy.wallet.BuildConfig
import com.ivy.wallet.Constants
import com.ivy.wallet.base.*
import com.ivy.wallet.logic.CustomerJourneyLogic
import com.ivy.wallet.model.TransactionType
import com.ivy.wallet.ui.analysis.AIAnalysisChatScreen
import com.ivy.wallet.ui.analytics.AnalyticsReport
import com.ivy.wallet.ui.applocked.AppLockedScreen
import com.ivy.wallet.ui.balance.BalanceScreen
import com.ivy.wallet.ui.bankintegrations.ConnectBankScreen
import com.ivy.wallet.ui.budget.BudgetScreen
import com.ivy.wallet.ui.category.CategoriesScreen
import com.ivy.wallet.ui.charts.ChartsScreen
import com.ivy.wallet.ui.csvimport.ImportCSVScreen
import com.ivy.wallet.ui.edit.EditTransactionScreen
import com.ivy.wallet.ui.loan.LoansScreen
import com.ivy.wallet.ui.loandetails.LoanDetailsScreen
import com.ivy.wallet.ui.main.MainScreen
import com.ivy.wallet.ui.onboarding.OnboardingScreen
import com.ivy.wallet.ui.paywall.PaywallScreen
import com.ivy.wallet.ui.planned.edit.EditPlannedScreen
import com.ivy.wallet.ui.planned.list.PlannedPaymentsScreen
import com.ivy.wallet.ui.reports.ReportScreen
import com.ivy.wallet.ui.search.SearchScreen
import com.ivy.wallet.ui.settings.SettingsScreen
import com.ivy.wallet.ui.statistic.level1.PieChartStatisticScreen
import com.ivy.wallet.ui.statistic.level2.ItemStatisticScreen
import com.ivy.wallet.ui.test.TestScreen
import com.ivy.wallet.ui.webView.WebViewScreen
import com.ivy.wallet.widget.AddTransactionWidget
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class IvyActivity : AppCompatActivity() {

    companion object {
        const val SUPPORT_EMAIL = "nfonjeannoel1@gmail.com"

        fun getIntent(context: Context): Intent = Intent(context, IvyActivity::class.java)

        fun addTransactionStart(context: Context, type: TransactionType): Intent =
            Intent(context, IvyActivity::class.java).apply {
                putExtra(IvyViewModel.EXTRA_ADD_TRANSACTION_TYPE, type)
            }
    }

    @Inject
    lateinit var ivyContext: IvyWalletCtx

    @Inject
    lateinit var navigation: Navigation

    @Inject
    lateinit var customerJourneyLogic: CustomerJourneyLogic

    private lateinit var googleSignInLauncher: ActivityResultLauncher<GoogleSignInClient>
    private lateinit var onGoogleSignInIdTokenResult: (idToken: String?) -> Unit

    private lateinit var createFileLauncher: ActivityResultLauncher<String>
    private lateinit var onFileCreated: (fileUri: Uri) -> Unit

    private lateinit var openFileLauncher: ActivityResultLauncher<Unit>
    private lateinit var onFileOpened: (fileUri: Uri) -> Unit


    private val viewModel: IvyViewModel by viewModels()

    private val SMS_PERMISSION_CODE = 101

    private fun checkForSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), SMS_PERMISSION_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                // Permission denied
            }
        }
    }

    @OptIn(
        ExperimentalAnimationApi::class,
        ExperimentalFoundationApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupActivityForResultLaunchers()

        // Make the app drawing area fullscreen (draw behind status and nav bars)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupDatePicker()
        setupTimePicker()
        checkForSmsPermission()

        AddTransactionWidget.updateBroadcast(this)

        setContent {
            val viewModel: IvyViewModel = viewModel()
            val isSystemInDarkTheme = isSystemInDarkTheme()

            LaunchedEffect(isSystemInDarkTheme) {
                viewModel.start(isSystemInDarkTheme, intent)
//                viewModel.initBilling(this@IvyActivity)
            }

            IvyUI(
                design = appDesign(ivyContext)
            ) {
                UI(viewModel)
            }
        }
    }

    @ExperimentalFoundationApi
    @ExperimentalAnimationApi
    @Composable
    private fun BoxWithConstraintsScope.UI(viewModel: IvyViewModel) {
        val appLocked by viewModel.appLocked.collectAsState()

        when (appLocked) {
            null -> {
                //display nothing
            }
            true -> {
                AppLockedScreen(
                    onShowOSBiometricsModal = {
                        authenticateWithOSBiometricsModal(
                            biometricPromptCallback = viewModel.handleBiometricAuthResult()
                        )
                    },
                    onContinueWithoutAuthentication = {
                        viewModel.unlockApp()
                    }
                )
            }
            false -> {
                NavigationRoot(navigation = navigation) { screen ->
                    Screens(screen)
                }
            }
        }
    }

    @ExperimentalFoundationApi
    @ExperimentalAnimationApi
    @Composable
    private fun BoxWithConstraintsScope.Screens(screen: Screen?) {
        when (screen) {
            is Main -> MainScreen(screen = screen)
            is Onboarding -> OnboardingScreen(screen = screen)
            is EditTransaction -> EditTransactionScreen(screen = screen)
            is ItemStatistic -> ItemStatisticScreen(screen = screen)
            is PieChartStatistic -> PieChartStatisticScreen(screen = screen)
            is AIAnalysisChat -> AIAnalysisChatScreen(screen = screen)
            is Categories -> CategoriesScreen(screen = screen)
            is Settings -> SettingsScreen(screen = screen)
            is PlannedPayments -> PlannedPaymentsScreen(screen = screen)
            is EditPlanned -> EditPlannedScreen(screen = screen)
            is BalanceScreen -> BalanceScreen(screen = screen)
            is Paywall -> PaywallScreen(
                screen = screen,
                activity = this@IvyActivity
            )
            is Test -> TestScreen(screen = screen)
            is Charts -> ChartsScreen(screen = screen)
            is AnalyticsReport -> AnalyticsReport(screen = screen)
            is Import -> ImportCSVScreen(screen = screen)
            is ConnectBank -> ConnectBankScreen(screen = screen)
            is Report -> ReportScreen(screen = screen)
            is BudgetScreen -> BudgetScreen(screen = screen)
            is Loans -> LoansScreen(screen = screen)
            is LoanDetails -> LoanDetailsScreen(screen = screen)
            is Search -> SearchScreen(screen = screen)
            is IvyWebView -> WebViewScreen(screen = screen)
            null -> {
            }
        }
    }

    private fun setupDatePicker() {
        ivyContext.onShowDatePicker = { minDate,
                                        maxDate,
                                        initialDate,
                                        onDatePicked ->
            val picker = DatePickerDialog(this)

            if (minDate != null) {
                picker.datePicker.minDate = minDate.atTime(12, 0).toEpochMilli()
            }

            if (maxDate != null) {
                picker.datePicker.maxDate = maxDate.atTime(12, 0).toEpochMilli()
            }

            picker.setOnDateSetListener { _, year, month, dayOfMonth ->
                Timber.i("Date picked: $year year $month month day $dayOfMonth")
                onDatePicked(LocalDate.of(year, month + 1, dayOfMonth))
            }
            picker.show()

            if (initialDate != null) {
                picker.updateDate(
                    initialDate.year,
                    //month-1 because LocalDate start from 1 and date picker starts from 0
                    initialDate.monthValue - 1,
                    initialDate.dayOfMonth
                )
            }
        }
    }

    private fun setupTimePicker() {
        ivyContext.onShowTimePicker = { onTimePicked ->
            val nowLocal = timeNowLocal()
            val picker = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    onTimePicked(
                        LocalTime.of(hourOfDay, minute)
                            .convertLocalToUTC().withSecond(0)
                    )
                },
                nowLocal.hour, nowLocal.minute, DateFormat.is24HourFormat(this)
            )
            picker.show()
        }
    }

    private fun setupActivityForResultLaunchers() {
        googleSignInLauncher()

        createFileLauncher()

        openFileLauncher()
    }

    private fun googleSignInLauncher() {
        googleSignInLauncher = activityForResultLauncher(
            createIntent = { _, client ->
                client.signInIntent
            }
        ) { _, intent ->
            try {
                val task: Task<GoogleSignInAccount> =
                    GoogleSignIn.getSignedInAccountFromIntent(intent)
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                Timber.d("idToken = $idToken")

                onGoogleSignInIdTokenResult(idToken)
            } catch (e: ApiException) {
                e.sendToCrashlytics("GOOGLE_SIGN_IN - registerGoogleSignInContract(): ApiException")
                e.printStackTrace()
                onGoogleSignInIdTokenResult(null)
            }
        }

        ivyContext.googleSignIn = { idTokenResult: (String?) -> Unit ->
            onGoogleSignInIdTokenResult = idTokenResult

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestIdToken("364763737033-t1d2qe7s0s8597k7anu3sb2nq79ot5tp.apps.googleusercontent.com")
                .build()
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            googleSignInLauncher.launch(googleSignInClient)
        }
    }

    private fun createFileLauncher() {
        createFileLauncher = activityForResultLauncher(
            createIntent = { _, fileName ->
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/csv"
                    putExtra(Intent.EXTRA_TITLE, fileName)

                    // Optionally, specify a URI for the directory that should be opened in
                    // the system file picker before your app creates the document.
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            .toURI()
                    )
                }
            }
        ) { _, intent ->
            intent?.data?.also {
                onFileCreated(it)
            }
        }

        ivyContext.createNewFile = { fileName, onFileCreatedCallback ->
            onFileCreated = onFileCreatedCallback

            createFileLauncher.launch(fileName)
        }
    }

    private fun openFileLauncher() {
        openFileLauncher = simpleActivityForResultLauncher(
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        ) { _, intent ->
            intent?.data?.also {
                onFileOpened(it)
            }
        }

        ivyContext.openFile = { onFileOpenedCallback ->
            onFileOpened = onFileOpenedCallback

            openFileLauncher.launch(Unit)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (viewModel.isAppLockEnabled() && !hasFocus) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isAppLockEnabled())
            viewModel.checkUserInactiveTimeStatus()
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.isAppLockEnabled())
            viewModel.startUserInactiveTimeCounter()
    }

    private fun authenticateWithOSBiometricsModal(
        biometricPromptCallback: BiometricPrompt.AuthenticationCallback
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            biometricPromptCallback
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(
                "Authentication required"
            )
            .setSubtitle(
                "Prove that you have access to this device to unlock the app."
            )
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .setConfirmationRequired(false)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onBackPressed() {
        if (viewModel.isAppLocked()) {
            super.onBackPressed()
        } else {
            if (!navigation.onBackPressed()) {
                super.onBackPressed()
            }
        }
    }


    //Helpers for Compose UI
    fun contactSupport() {
        val caseNumber: Int = Random().nextInt(100) + 100

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // only email apps should handle this

            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(
                Intent.EXTRA_SUBJECT, "Bucket Money Support Request #" + caseNumber +
                        "0" + BuildConfig.VERSION_CODE
            )
            putExtra(Intent.EXTRA_TEXT, "")
        }

        try {
            startActivity(emailIntent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Email: $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
        }
    }

    fun openUrlInBrowser(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW)
            browserIntent.data = Uri.parse(url)
            startActivity(browserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            e.sendToCrashlytics("Cannot open URL in browser, intent not supported.")
            Toast.makeText(
                this,
                "No browser app found. Visit manually: $url",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun shareIvyWallet() {
        val share = Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, Constants.SHARE_TEXT)
                type = "text/plain"
            },
            null
        )
        startActivity(share)
    }


    fun openGooglePlayAppPage(appId: String = packageName) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId")))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appId")
                )
            )
        }
    }

    fun shareCSVFile(fileUri: Uri) {
        val intent = Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = "text/csv"
            }, null
        )
        startActivity(intent)
    }

    fun shareZipFile(fileUri: Uri) {
        val intent = Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = "application/zip"
            }, null
        )
        startActivity(intent)
    }

    fun reviewIvyWallet(dismissReviewCard: Boolean) {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    // The flow has finished. The API does not indicate whether the user
                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                    // matter the result, we continue our app flow.
                    if (dismissReviewCard) {
                        customerJourneyLogic.dismissCard(CustomerJourneyLogic.rateUsCard())
                    }

                    openGooglePlayAppPage()
                }
            } else {
                openGooglePlayAppPage()
            }
        }
    }

    fun <T> pinWidget(widget: Class<T>) {
        val appWidgetManager: AppWidgetManager = this.getSystemService(AppWidgetManager::class.java)
        val addTransactionWidget = ComponentName(this, widget)
        appWidgetManager.requestPinAppWidget(addTransactionWidget, null, null)
    }
}

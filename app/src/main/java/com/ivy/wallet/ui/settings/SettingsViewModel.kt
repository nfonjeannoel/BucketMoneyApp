package com.ivy.wallet.ui.settings

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.ivy.design.navigation.Navigation
import com.ivy.wallet.analytics.IvyAnalytics
import com.ivy.wallet.base.*
import com.ivy.wallet.logic.LogoutLogic
import com.ivy.wallet.logic.csv.ExportCSVLogic
import com.ivy.wallet.logic.currency.ExchangeRatesLogic
import com.ivy.wallet.logic.zip.ExportZipLogic
import com.ivy.wallet.model.AuthProviderType
import com.ivy.wallet.model.analytics.AnalyticsEvent
import com.ivy.wallet.model.entity.User
import com.ivy.wallet.network.FCMClient
import com.ivy.wallet.network.RestClient
import com.ivy.wallet.network.request.auth.AuthResponse
import com.ivy.wallet.network.request.auth.GoogleSignInRequest
import com.ivy.wallet.network.request.github.OpenIssueRequest
import com.ivy.wallet.persistence.SharedPrefs
import com.ivy.wallet.persistence.dao.SettingsDao
import com.ivy.wallet.persistence.dao.UserDao
import com.ivy.wallet.session.IvySession
import com.ivy.wallet.sync.IvySync
import com.ivy.wallet.ui.AIAnalysisChat
import com.ivy.wallet.ui.IvyActivity
import com.ivy.wallet.ui.IvyWalletCtx
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val ivySession: IvySession,
    private val userDao: UserDao,
    private val ivyContext: IvyWalletCtx,
    private val ivySync: IvySync,
    private val exportCSVLogic: ExportCSVLogic,
    private val restClient: RestClient,
    private val fcmClient: FCMClient,
    private val ivyAnalytics: IvyAnalytics,
    private val exchangeRatesLogic: ExchangeRatesLogic,
    private val logoutLogic: LogoutLogic,
    private val sharedPrefs: SharedPrefs,
    private val exportZipLogic: ExportZipLogic,
    private val nav: Navigation,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("1016546210568-mm4urj4augbnvvr7elqsnvig2h2j6bv3.apps.googleusercontent.com")
            .requestEmail()
            .build()

        GoogleSignIn.getClient(appContext, gso)
    }

    private val _user = MutableLiveData<User?>()
    val user = _user.asLiveData()

    private val _nameLocalAccount = MutableLiveData<String?>()
    val nameLocalAccount = _nameLocalAccount.asLiveData()

    private val _opSync = MutableLiveData<OpResult<Boolean>>()
    val opSync = _opSync.asLiveData()

    private val _currencyCode = MutableLiveData<String>()
    val currencyCode = _currencyCode.asLiveData()

    private val _lockApp = MutableLiveData<Boolean>()
    val lockApp = _lockApp.asLiveData()

    private val _showNotifications = MutableStateFlow(true)
    val showNotifications = _showNotifications.asStateFlow()

    private val _showIncomingNotifications = MutableStateFlow(true)
    val showIncomingNotifications = _showIncomingNotifications.asStateFlow()

    private val _progressState = MutableStateFlow(false)
    val progressState = _progressState.asStateFlow()

    private val _startDateOfMonth = MutableLiveData<Int>()
    val startDateOfMonth = _startDateOfMonth

    fun start() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            val settings = ioThread { settingsDao.findFirst() }

            _nameLocalAccount.value = settings.name

            ivyContext.initStartDayOfMonthInMemory(sharedPrefs = sharedPrefs)
            _startDateOfMonth.value = ivyContext.startDayOfMonth

            _user.value = ioThread {
                val userId = ivySession.getUserIdSafe()
                if (userId != null) userDao.findById(userId) else null
            }
            _currencyCode.value = settings.currency

            _lockApp.value = sharedPrefs.getBoolean(SharedPrefs.APP_LOCK_ENABLED, false)

            _showNotifications.value = sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)

            _opSync.value = OpResult.success(ioThread { ivySync.isSynced() })

            TestIdlingResource.decrement()
        }
    }

    fun sync() {
        viewModelScope.launch {
            TestIdlingResource.increment()
            return@launch

            _opSync.value = OpResult.loading()

            ioThread {
                ivySync.sync()
            }

            _opSync.value = OpResult.success(ioThread { ivySync.isSynced() })

            TestIdlingResource.decrement()
        }
    }


    fun setName(newName: String) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                settingsDao.save(
                    settingsDao.findFirst().copy(
                        name = newName
                    )
                )
            }
            start()

            TestIdlingResource.decrement()
        }
    }

    fun setCurrency(newCurrency: String) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                settingsDao.save(
                    settingsDao.findFirst().copy(
                        currency = newCurrency
                    )
                )

                exchangeRatesLogic.sync(baseCurrency = newCurrency)
            }
            start()

            TestIdlingResource.decrement()
        }
    }

    fun exportToCSV(context: Context) {
        ivyContext.createNewFile(
            "Bucket Money (${
                timeNowUTC().formatNicelyWithTime(noWeekDay = true)
            }).csv"
        ) { fileUri ->
            viewModelScope.launch {
                TestIdlingResource.increment()

                exportCSVLogic.exportToFile(
                    context = context,
                    fileUri = fileUri
                )

                (context as IvyActivity).shareCSVFile(
                    fileUri = fileUri
                )

                TestIdlingResource.decrement()
            }
        }
    }

    fun exportToZip(context: Context) {
        ivyContext.createNewFile(
            "Ivy Wallet (${
                timeNowUTC().formatNicelyWithTime(noWeekDay = true)
            }).zip"
        ) { fileUri ->
            viewModelScope.launch(Dispatchers.IO) {
                TestIdlingResource.increment()

                _progressState.value = true
                exportZipLogic.exportToFile(context = context, zipFileUri = fileUri)
                _progressState.value = false

                uiThread {
                    (context as IvyActivity).shareZipFile(
                        fileUri = fileUri
                    )
                }

                TestIdlingResource.decrement()
            }
        }
    }


    fun setStartDateOfMonth(startDate: Int) {
        if (startDate in 1..31) {
            TestIdlingResource.increment()

            ivyContext.updateStartDayOfMonthWithPersistence(
                sharedPrefs = sharedPrefs,
                startDayOfMonth = startDate
            )
            _startDateOfMonth.value = startDate

            TestIdlingResource.decrement()
        }
    }

    fun logout() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            logoutLogic.logout()

            TestIdlingResource.decrement()
        }
    }

    fun login() {
        ivyContext.googleSignIn { idToken ->
            if (idToken != null) {
                viewModelScope.launch {
                    TestIdlingResource.increment()

                    try {
                        val authResponse = restClient.authService.googleSignIn(
                            GoogleSignInRequest(
                                googleIdToken = idToken,
                                fcmToken = fcmClient.fcmToken()
                            )
                        )

                        ioThread {
                            ivySession.initiate(authResponse)

                            settingsDao.save(
                                settingsDao.findFirst().copy(
                                    name = authResponse.user.firstName
                                )
                            )
                        }

                        start()

                        ioThread {
                            ivyAnalytics.logEvent(AnalyticsEvent.LOGIN_FROM_SETTINGS)
                        }

                        sync()
                    } catch (e: Exception) {
                        e.sendToCrashlytics("Settings - GOOGLE_SIGN_IN ERROR: generic exception when logging with GOOGLE")
                        e.printStackTrace()
                        Timber.e("Settings - Login with Google failed on Ivy server - ${e.message}")
                    }

                    TestIdlingResource.decrement()
                }
            } else {
                sendToCrashlytics("Settings - GOOGLE_SIGN_IN ERROR: idToken is null!!")
                Timber.e("Settings - Login with Google failed while getting idToken")
            }
        }
    }

    fun loginWithGoogleNew(account: GoogleSignInAccount?) {
        TestIdlingResource.increment()
        viewModelScope.launch {
//            session.initiate(authResponse)
            Timber.d("Login with Google successful ${account?.email} ${account?.displayName}" )

            saveUser(account)
            start()


//            _opGoogleSignIn.value = OpResult.success(Unit)
//            router.googleLoginNext()
        }



//        _opGoogleSignIn.value = null

        TestIdlingResource.decrement()
    }

    private suspend fun saveUser(account: GoogleSignInAccount?) {
        val user = User(
            email = account?.email ?: "",
            authProviderType = AuthProviderType.GOOGLE,
            firstName = account?.givenName ?: "",
            lastName = account?.familyName,
            profilePicture = account?.photoUrl?.toString(),
            color = 0,
            testUser = false,
            id = UUID.randomUUID()
        )

        ioThread {
            ivySession.initiate(AuthResponse(user, ""))
            settingsDao.save(
                settingsDao.findFirst().copy(
                    name = account?.displayName ?: account?.familyName ?: account?.givenName ?: ""
                )
            )
        }
    }


    fun setLockApp(lockApp: Boolean) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            sharedPrefs.putBoolean(SharedPrefs.APP_LOCK_ENABLED, lockApp)
            _lockApp.value = lockApp

            TestIdlingResource.decrement()
        }
    }

    fun setShowNotifications(showNotifications: Boolean) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            sharedPrefs.putBoolean(SharedPrefs.SHOW_NOTIFICATIONS, showNotifications)
            _showNotifications.value = showNotifications

            TestIdlingResource.decrement()
        }
    }

    fun setShowIncomingNotifications(showIncomingNotifications: Boolean) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            sharedPrefs.putBoolean(SharedPrefs.SHOW_INCOMING_NOTIFICATIONS, showIncomingNotifications)
            _showIncomingNotifications.value = showIncomingNotifications

            TestIdlingResource.decrement()
        }
    }

    fun requestFeature(
        ivyActivity: IvyActivity,
        title: String,
        body: String
    ) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            try {
                val response = restClient.githubService.openIssue(
                    request = OpenIssueRequest(
                        title = title,
                        body = body,
                    )
                )

                //Returned: https://api.github.com/repos/octocat/Hello-World/issues/1347
                //Should open: https://github.com/octocat/Hello-World/issues/1347
                val issueUrl = response.url
                    .replace("api.github.com", "github.com")
                    .replace("/repos", "")

                ivyActivity.openUrlInBrowser(issueUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            TestIdlingResource.decrement()
        }
    }

    fun deleteAllUserData() {
        viewModelScope.launch {
            try {
                restClient.nukeService.deleteAllUserData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            logout()
        }
    }


    fun onAiChatClicked() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            nav.navigateTo(
                AIAnalysisChat()
            )

            TestIdlingResource.decrement()
        }
    }
}
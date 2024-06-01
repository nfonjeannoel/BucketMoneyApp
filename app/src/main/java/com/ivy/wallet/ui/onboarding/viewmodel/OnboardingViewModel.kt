package com.ivy.wallet.ui.onboarding.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.design.l0_system.Theme
import com.ivy.design.navigation.Navigation
import com.ivy.wallet.R
import com.ivy.wallet.analytics.IvyAnalytics
import com.ivy.wallet.base.*
import com.ivy.wallet.logic.*
import com.ivy.wallet.logic.currency.ExchangeRatesLogic
import com.ivy.wallet.logic.model.CreateAccountData
import com.ivy.wallet.logic.model.CreateCategoryData
import com.ivy.wallet.logic.notification.TransactionReminderLogic
import com.ivy.wallet.model.IvyCurrency
import com.ivy.wallet.model.entity.Account
import com.ivy.wallet.model.entity.Category
import com.ivy.wallet.model.entity.Settings
import com.ivy.wallet.network.FCMClient
import com.ivy.wallet.network.RestClient
import com.ivy.wallet.network.request.auth.GoogleSignInRequest
import com.ivy.wallet.persistence.SharedPrefs
import com.ivy.wallet.persistence.dao.AccountDao
import com.ivy.wallet.persistence.dao.CategoryDao
import com.ivy.wallet.persistence.dao.SettingsDao
import com.ivy.wallet.session.IvySession
import com.ivy.wallet.sync.IvySync
import com.ivy.wallet.ui.IvyWalletCtx
import com.ivy.wallet.ui.Onboarding
import com.ivy.wallet.ui.onboarding.OnboardingState
import com.ivy.wallet.ui.onboarding.model.AccountBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val ivyContext: IvyWalletCtx,
    private val nav: Navigation,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val restClient: RestClient,
    private val fcmClient: FCMClient,
    private val session: IvySession,
    private val accountLogic: WalletAccountLogic,
    private val categoryCreator: CategoryCreator,
    private val categoryDao: CategoryDao,
    private val accountCreator: AccountCreator,
    @ApplicationContext private val appContext: Context,

    //Only OnboardingRouter stuff
    sharedPrefs: SharedPrefs,
    ivySync: IvySync,
    ivyAnalytics: IvyAnalytics,
    transactionReminderLogic: TransactionReminderLogic,
    preloadDataLogic: PreloadDataLogic,
    exchangeRatesLogic: ExchangeRatesLogic,
    logoutLogic: LogoutLogic
) : ViewModel() {

    private val _state = MutableLiveData(OnboardingState.SPLASH)
    val state = _state.asLiveData()

    private val _currency = MutableLiveData<IvyCurrency>()
    val currency = _currency.asLiveData()

    private val _opGoogleSignIn = MutableLiveData<OpResult<Unit>?>()
    val opGoogleSignIn = _opGoogleSignIn.asLiveData()

    private val _accounts = MutableLiveData<List<AccountBalance>>()
    val accounts = _accounts.asLiveData()

    private val _accountSuggestions = MutableLiveData<List<CreateAccountData>>()
    val accountSuggestions = _accountSuggestions.asLiveData()

    private val _categories = MutableLiveData<List<Category>>()
    val categories = _categories.asLiveData()

    private val _categorySuggestions = MutableLiveData<List<CreateCategoryData>>()
    val categorySuggestions = _categorySuggestions.asLiveData()

    private val router = OnboardingRouter(
        _state = _state,
        _opGoogleSignIn = _opGoogleSignIn,
        _accounts = _accounts,
        _accountSuggestions = _accountSuggestions,
        _categories = _categories,
        _categorySuggestions = _categorySuggestions,

        ivyContext = ivyContext,
        nav = nav,
        ivyAnalytics = ivyAnalytics,
        exchangeRatesLogic = exchangeRatesLogic,
        accountDao = accountDao,
        sharedPrefs = sharedPrefs,
        categoryDao = categoryDao,
        ivySync = ivySync,
        preloadDataLogic = preloadDataLogic,
        transactionReminderLogic = transactionReminderLogic,
        logoutLogic = logoutLogic
    )

    fun start(screen: Onboarding, isSystemDarkMode: Boolean) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            initiateSettings(isSystemDarkMode)

            router.initBackHandling(
                screen = screen,
                viewModelScope = viewModelScope,
                restartOnboarding = {
                    start(screen, isSystemDarkMode)
                }
            )

            router.splashNext()

            TestIdlingResource.decrement()
        }
    }

    private suspend fun initiateSettings(isSystemDarkMode: Boolean) {
        val defaultCurrency = IvyCurrency.getDefault()
        _currency.value = defaultCurrency

        ioThread {
            TestIdlingResource.increment()

            if (settingsDao.findAll().isEmpty()) {
                settingsDao.save(
                    Settings(
                        theme = if (isSystemDarkMode) Theme.DARK else Theme.LIGHT,
                        name = "",
                        currency = defaultCurrency.code,
                        bufferAmount = 1000.0
                    )
                )
            }

            TestIdlingResource.decrement()
        }
    }

    //Step 1 ---------------------------------------------------------------------------------------
    fun loginWithGoogle() {
        // implement custom backend
        _opGoogleSignIn.value =
            OpResult.faliure(appContext.getString(R.string.failed_to_connect_to_server))
        return
        ivyContext.googleSignIn { idToken ->
            if (idToken != null) {
                _opGoogleSignIn.value = OpResult.loading()
                viewModelScope.launch {
                    TestIdlingResource.increment()

                    try {
                        loginWithGoogleOnServer(idToken)

                        router.googleLoginNext()

                        _opGoogleSignIn.value = null //reset login with Google operation state
                    } catch (e: Exception) {
                        e.sendToCrashlytics("GOOGLE_SIGN_IN ERROR: generic exception when logging with GOOGLE")
                        e.printStackTrace()
                        Timber.e("Login with Google failed on Ivy server - ${e.message}")
                        _opGoogleSignIn.value = OpResult.failure(e)
                    }

                    TestIdlingResource.decrement()
                }
            } else {
                sendToCrashlytics("GOOGLE_SIGN_IN ERROR: idToken is null!!")
                Timber.e("Login with Google failed while getting idToken")
                _opGoogleSignIn.value = OpResult.faliure("Login with Google failed, try again.")
            }
        }
    }

    private suspend fun loginWithGoogleOnServer(idToken: String) {
        TestIdlingResource.increment()

        val authResponse = restClient.authService.googleSignIn(
            GoogleSignInRequest(
                googleIdToken = idToken,
                fcmToken = fcmClient.fcmToken()
            )
        )

        ioThread {
            session.initiate(authResponse)

            settingsDao.save(
                settingsDao.findFirst().copy(
                    name = authResponse.user.firstName
                )
            )
        }

        _opGoogleSignIn.value = OpResult.success(Unit)

        TestIdlingResource.decrement()
    }

    fun loginOfflineAccount() {
        viewModelScope.launch {
            TestIdlingResource.increment()
            router.offlineAccountNext()
            TestIdlingResource.decrement()
        }
    }
    //Step 1 ---------------------------------------------------------------------------------------


    //Step 2 ---------------------------------------------------------------------------------------
    fun startImport() {
        router.startImport()
    }

    fun importSkip() {
        router.importSkip()
    }

    fun importFinished(success: Boolean) {
        router.importFinished(success)
    }

    fun startFresh() {
        router.startFresh()
    }
    //Step 2 ---------------------------------------------------------------------------------------


    fun setBaseCurrency(baseCurrency: IvyCurrency) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            updateBaseCurrency(baseCurrency)

            router.setBaseCurrencyNext(
                baseCurrency = baseCurrency,
                accountsWithBalance = { accountsWithBalance() }
            )

            TestIdlingResource.decrement()
        }
    }

    private suspend fun updateBaseCurrency(baseCurrency: IvyCurrency) {
        ioThread {
            TestIdlingResource.increment()

            settingsDao.save(
                settingsDao.findFirst().copy(
                    currency = baseCurrency.code
                )
            )

            TestIdlingResource.decrement()
        }
        _currency.value = baseCurrency
    }

    //--------------------- Accounts ---------------------------------------------------------------
    fun editAccount(account: Account, newBalance: Double) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            accountCreator.editAccount(account, newBalance) {
                _accounts.value = accountsWithBalance()
            }

            TestIdlingResource.decrement()
        }
    }


    fun createAccount(data: CreateAccountData) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            accountCreator.createAccount(data) {
                _accounts.value = accountsWithBalance()
            }

            TestIdlingResource.decrement()
        }
    }

    private suspend fun accountsWithBalance(): List<AccountBalance> = ioThread {
        accountDao.findAll()
            .map {
                AccountBalance(
                    account = it,
                    balance = accountLogic.calculateAccountBalance(it)
                )
            }
    }

    fun onAddAccountsDone() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            router.accountsNext()

            TestIdlingResource.decrement()
        }
    }

    fun onAddAccountsSkip() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            router.accountsSkip()

            TestIdlingResource.decrement()
        }
    }
    //--------------------- Accounts ---------------------------------------------------------------

    //---------------------------- Categories ------------------------------------------------------
    fun editCategory(updatedCategory: Category) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            categoryCreator.editCategory(updatedCategory) {
                _categories.value = ioThread { categoryDao.findAll() }!!
            }

            TestIdlingResource.decrement()
        }
    }

    fun createCategory(data: CreateCategoryData) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            categoryCreator.createCategory(data) {
                _categories.value = ioThread { categoryDao.findAll() }!!
            }

            TestIdlingResource.decrement()
        }
    }

    fun onAddCategoriesDone() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            router.categoriesNext(baseCurrency = currency.value)

            TestIdlingResource.decrement()
        }
    }

    fun onAddCategoriesSkip() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            router.categoriesSkip(baseCurrency = currency.value)

            TestIdlingResource.decrement()
        }
    }
    //---------------------------- Categories ------------------------------------------------------
}
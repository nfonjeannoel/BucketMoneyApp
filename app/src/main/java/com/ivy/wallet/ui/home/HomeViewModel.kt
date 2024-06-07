package com.ivy.wallet.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.ivy.design.api.navigation
import com.ivy.design.l0_system.Theme
import com.ivy.design.navigation.Navigation
import com.ivy.wallet.Constants
import com.ivy.wallet.base.TestIdlingResource
import com.ivy.wallet.base.dateNowUTC
import com.ivy.wallet.base.ioThread
import com.ivy.wallet.base.readOnly
import com.ivy.wallet.functional.data.WalletDAOs
import com.ivy.wallet.functional.wallet.calculateWalletBalance
import com.ivy.wallet.functional.wallet.calculateWalletIncomeExpense
import com.ivy.wallet.functional.wallet.historyWithDateDividers
import com.ivy.wallet.functional.wallet.walletBufferDiff
import com.ivy.wallet.logic.CustomerJourneyLogic
import com.ivy.wallet.logic.PlannedPaymentsLogic
import com.ivy.wallet.logic.WalletLogic
import com.ivy.wallet.logic.currency.ExchangeRatesLogic
import com.ivy.wallet.logic.model.CustomerJourneyCardData
import com.ivy.wallet.model.TransactionHistoryItem
import com.ivy.wallet.model.entity.Account
import com.ivy.wallet.model.entity.Category
import com.ivy.wallet.model.entity.Transaction
import com.ivy.wallet.persistence.SharedPrefs
import com.ivy.wallet.persistence.dao.CategoryDao
import com.ivy.wallet.persistence.dao.SettingsDao
import com.ivy.wallet.ui.AIAnalysisChat
import com.ivy.wallet.ui.BalanceScreen
import com.ivy.wallet.ui.IvyWalletCtx
import com.ivy.wallet.ui.Main
import com.ivy.wallet.ui.main.MainTab
import com.ivy.wallet.ui.onboarding.model.TimePeriod
import com.ivy.wallet.ui.onboarding.model.toCloseTimeRange
import com.ivy.wallet.ui.theme.modal.model.OpenAiPrompt
import com.ivy.wallet.ui.vibratePhone
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val transactionsString: String? = null,
    val aiInsights: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

data class ChatMessageEntity(
    val id: UUID = UUID.randomUUID(),
    var content: String,
    val type: ChatMessageType,
    val timestamp: Long = System.currentTimeMillis()
)


enum class ChatMessageType {
    SENT, RECEIVED
}


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val walletDAOs: WalletDAOs,
    private val settingsDao: SettingsDao,
    private val categoryDao: CategoryDao,
    private val walletLogic: WalletLogic,
    private val ivyContext: IvyWalletCtx,
    private val nav: Navigation,
    private val exchangeRatesLogic: ExchangeRatesLogic,
    private val plannedPaymentsLogic: PlannedPaymentsLogic,
    private val customerJourneyLogic: CustomerJourneyLogic,
    private val sharedPrefs: SharedPrefs,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val TAG = "HomeViewModel"
    val TEMPERATURE = 0.2
    val MAX_TOKENS = 600
    val FREQUENCY_PENALTY = 0.5
    val PRESENCE_PENALTY = 0.5


    private val _chatUiState = MutableStateFlow(ChatUiState())
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _theme = MutableStateFlow(Theme.LIGHT)
    val theme = _theme.readOnly()

    private val _name = MutableStateFlow("")
    val name = _name.readOnly()

    private val _period = MutableStateFlow(ivyContext.selectedPeriod)
    val period = _period.readOnly()

    private val _baseCurrencyCode = MutableStateFlow("")
    val baseCurrencyCode = _baseCurrencyCode.readOnly()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.readOnly()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts = _accounts.readOnly()

    private val _balance = MutableStateFlow(0.0)
    val balance = _balance.readOnly()

    private val _buffer = MutableStateFlow(0.0)
    val buffer = _buffer.readOnly()

    private val _bufferDiff = MutableStateFlow(0.0)
    val bufferDiff = _bufferDiff.readOnly()

    private val _monthlyIncome = MutableStateFlow(0.0)
    val monthlyIncome = _monthlyIncome.readOnly()

    private val _monthlyExpenses = MutableStateFlow(0.0)
    val monthlyExpenses = _monthlyExpenses.readOnly()

    //Upcoming
    private val _upcoming = MutableStateFlow<List<Transaction>>(emptyList())
    val upcoming = _upcoming.readOnly()

    private val _upcomingIncome = MutableStateFlow(0.0)
    val upcomingIncome = _upcomingIncome.readOnly()

    private val _upcomingExpenses = MutableStateFlow(0.0)
    val upcomingExpenses = _upcomingExpenses.readOnly()

    private val _upcomingExpanded = MutableStateFlow(false)
    val upcomingExpanded = _upcomingExpanded.readOnly()

    //Overdue
    private val _overdue = MutableStateFlow<List<Transaction>>(emptyList())
    val overdue = _overdue.readOnly()

    private val _overdueIncome = MutableStateFlow(0.0)
    val overdueIncome = _overdueIncome.readOnly()

    private val _overdueExpenses = MutableStateFlow(0.0)
    val overdueExpenses = _overdueExpenses.readOnly()

    private val _overdueExpanded = MutableStateFlow(true)
    val overdueExpanded = _overdueExpanded.readOnly()

    //History
    private val _history = MutableStateFlow<List<TransactionHistoryItem>>(emptyList())
    val history = _history.readOnly()

    //Customer Journey
    private val _customerJourneyCards = MutableStateFlow<List<CustomerJourneyCardData>>(emptyList())
    val customerJourneyCards = _customerJourneyCards.readOnly()

    private var aiResponseJob: Job? = null

    fun start() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            val startDayOfMonth = ivyContext.initStartDayOfMonthInMemory(sharedPrefs = sharedPrefs)
            load(
                period = ivyContext.initSelectedPeriodInMemory(
                    startDayOfMonth = startDayOfMonth
                )
            )

            TestIdlingResource.decrement()
        }
    }

    private fun load(period: TimePeriod = ivyContext.selectedPeriod) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            val settings = ioThread { settingsDao.findFirst() }

            _theme.value = settings.theme

            //This method is used to restore the theme when user imports locally backed up data
            loadNewTheme()

            _name.value = settings.name
            _baseCurrencyCode.value = settings.currency

            _categories.value = ioThread { categoryDao.findAll() }
            _accounts.value = ioThread { walletDAOs.accountDao.findAll() }

            _period.value = period
            val timeRange = period.toRange(ivyContext.startDayOfMonth)

            _balance.value = ioThread {
                calculateWalletBalance(
                    walletDAOs = walletDAOs,
                    baseCurrencyCode = settings.currency
                ).value.toDouble()
            }

            _buffer.value = settings.bufferAmount
            _bufferDiff.value = ioThread {
                walletBufferDiff(
                    settings = settings,
                    balance = balance.value.toBigDecimal()
                ).toDouble()
            }

            val incomeExpensePair = ioThread {
                calculateWalletIncomeExpense(
                    walletDAOs = walletDAOs,
                    baseCurrencyCode = baseCurrencyCode.value,
                    range = timeRange.toCloseTimeRange()
                ).value
            }
            _monthlyIncome.value = incomeExpensePair.income.toDouble()
            _monthlyExpenses.value = incomeExpensePair.expense.toDouble()

            _upcomingIncome.value = ioThread { walletLogic.calculateUpcomingIncome(timeRange) }
            _upcomingExpenses.value =
                ioThread { walletLogic.calculateUpcomingExpenses(timeRange) }
            _upcoming.value = ioThread { walletLogic.upcomingTransactions(timeRange) }

            _overdueIncome.value = ioThread { walletLogic.calculateOverdueIncome(timeRange) }
            _overdueExpenses.value = ioThread { walletLogic.calculateOverdueExpenses(timeRange) }
            _overdue.value = ioThread { walletLogic.overdueTransactions(timeRange) }

            _history.value = ioThread {
                historyWithDateDividers(
                    walletDAOs = walletDAOs,
                    baseCurrencyCode = baseCurrencyCode.value,
                    range = timeRange.toCloseTimeRange()
                )
            }

            _customerJourneyCards.value = ioThread { customerJourneyLogic.loadCards() }

            TestIdlingResource.decrement()
        }
    }

    fun getCompletion() {
        aiResponseJob = viewModelScope.launch {
            _chatUiState.value = _chatUiState.value.copy(
                loading = true,
                error = "",
                transactionsString = "",
                aiInsights = "",
            )
            Timber.tag(TAG).d("getCompletion: ${_chatUiState.value.transactionsString}")
            val previousMessages: MutableList<ChatMessageEntity> = mutableListOf()

            _history.value.forEachIndexed { index, transactionHistoryItem ->
                if (transactionHistoryItem is Transaction) {
                    val transactionString = "$index.${transactionHistoryItem.toChatGptPrompt()}"
                    _chatUiState.value = _chatUiState.value.copy(
                        transactionsString = _chatUiState.value.transactionsString + transactionString
                    )
                    previousMessages.add(
                        ChatMessageEntity(
                            content = transactionString,
                            type = ChatMessageType.SENT
                        )
                    )
                }
            }

            if (_chatUiState.value.transactionsString.isNullOrEmpty()) {
                _chatUiState.value = _chatUiState.value.copy(
                    loading = false,
                )
                return@launch
            }

            val chatMessageContext: MutableList<ChatMessage> = mutableListOf()

            var totalCharCount = 0

            previousMessages.forEach { chatMessageEntity ->
                totalCharCount += chatMessageEntity.content.length
                if (totalCharCount > 3000) {
                    return@forEach
                }
                chatMessageContext.add(
                    ChatMessage(
                        role = ChatRole.User,
                        content = chatMessageEntity.content
                    )
                )
            }

//            Timber.tag(TAG).d(chatMessageContext.toString())


            val extraPromptDetails = "Currency is ${baseCurrencyCode.value}."

            val openAI = OpenAI(Constants.OPEN_AI_API_KEY)
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId("gpt-3.5-turbo"),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = OpenAiPrompt.systemPrompt
                    ),
                    *chatMessageContext.toTypedArray(),
                    ChatMessage(
                        role = ChatRole.User,
                        content = OpenAiPrompt.userPrompt + extraPromptDetails
                    )
                ),
                temperature = TEMPERATURE,
                maxTokens = MAX_TOKENS,
                topP = 1.0,
                frequencyPenalty = FREQUENCY_PENALTY,
                presencePenalty = PRESENCE_PENALTY,
            )


            try {
                val completions: Flow<ChatCompletionChunk> =
                    openAI.chatCompletions(chatCompletionRequest)
                completions.collect { completionChunk ->
                    val response = completionChunk.choices[0].delta.content
//                    Timber.tag(TAG).d("response: $response")
                    response?.let {
                        _chatUiState.value = _chatUiState.value.copy(
                            aiInsights = _chatUiState.value.aiInsights + it,
                        )
                    }
                    context.vibratePhone(
                        amplitude = 10
                    )
                }
//                Timber.tag(TAG).d("Done with completion")
                _chatUiState.value = _chatUiState.value.copy(loading = false, error = "")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e)
                _chatUiState.value = _chatUiState.value.copy(
                    error = "Oops! Bucket Money AI had an issue connecting and can't provide insights right now. Remember, as Warren Buffett said, 'Do not save what is left after spending, but spend what is left after saving.' Stay tuned for updates!",
                    loading = false
                )

            }

        }

    }

    fun stopAiResponseJob() {
        aiResponseJob?.cancel()
    }

    private fun loadNewTheme() {
        ivyContext.switchTheme(_theme.value)
    }

    fun setUpcomingExpanded(expanded: Boolean) {
        _upcomingExpanded.value = expanded
    }

    fun setOverdueExpanded(expanded: Boolean) {
        _overdueExpanded.value = expanded
    }

    fun onBalanceClick() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            val hasTransactions = ioThread {
                walletDAOs.transactionDao.findAll_LIMIT_1().isNotEmpty()
            }
            if (hasTransactions) {
                //has transactions show him "Balance" screen
                nav.navigateTo(BalanceScreen)
            } else {
                //doesn't have transactions lead him to adjust balance
                ivyContext.selectMainTab(MainTab.ACCOUNTS)
                nav.navigateTo(Main)
            }

            TestIdlingResource.decrement()
        }
    }

    fun switchTheme() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            val newSettings = ioThread {
                val currentSettings = settingsDao.findFirst()
                val newSettings = currentSettings.copy(
                    theme = when (currentSettings.theme) {
                        Theme.LIGHT -> Theme.DARK
                        Theme.DARK -> Theme.AUTO
                        Theme.AUTO -> Theme.LIGHT
                    }
                )
                settingsDao.save(newSettings)
                newSettings
            }

            ivyContext.switchTheme(newSettings.theme)
            _theme.value = newSettings.theme

            TestIdlingResource.decrement()
        }
    }

    fun setBuffer(newBuffer: Double) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                settingsDao.save(
                    settingsDao.findFirst().copy(
                        bufferAmount = newBuffer
                    )
                )
            }
            load()

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
            load()

            TestIdlingResource.decrement()
        }
    }

    fun setPeriod(period: TimePeriod) {
        load(period = period)
    }

    fun chatClicked() {
        viewModelScope.launch {
            TestIdlingResource.increment()
            nav.navigateTo(
                AIAnalysisChat(
                    previousAiAnalysis = _chatUiState.value.aiInsights ?: "",
                )
            )
            TestIdlingResource.decrement()
        }
    }

    fun payOrGet(transaction: Transaction) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            plannedPaymentsLogic.payOrGet(transaction = transaction) {
                load()
            }

            TestIdlingResource.decrement()
        }
    }

    fun dismissCustomerJourneyCard(card: CustomerJourneyCardData) {
        customerJourneyLogic.dismissCard(card)
        load()
    }

    fun nextMonth() {
        val month = period.value.month
        val year = period.value.year ?: dateNowUTC().year
        if (month != null) {
            load(
                period = month.incrementMonthPeriod(ivyContext, 1L, year = year),
            )
        }
    }

    fun previousMonth() {
        val month = period.value.month
        val year = period.value.year ?: dateNowUTC().year
        if (month != null) {
            load(
                period = month.incrementMonthPeriod(ivyContext, -1L, year = year),
            )
        }
    }
}
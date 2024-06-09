package com.ivy.wallet.ui.analysis

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.ivy.wallet.Constants
import com.ivy.wallet.base.TestIdlingResource
import com.ivy.wallet.base.dateNowUTC
import com.ivy.wallet.base.ioThread
import com.ivy.wallet.base.isNotNullOrBlank
import com.ivy.wallet.base.readOnly
import com.ivy.wallet.functional.wallet.history
import com.ivy.wallet.model.entity.Transaction
import com.ivy.wallet.persistence.dao.SettingsDao
import com.ivy.wallet.persistence.dao.TransactionDao
import com.ivy.wallet.ui.AIAnalysisChat
import com.ivy.wallet.ui.IvyWalletCtx
import com.ivy.wallet.ui.home.ChatMessageEntity
import com.ivy.wallet.ui.home.ChatMessageType
import com.ivy.wallet.ui.home.ChatUiState
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
import javax.inject.Inject

object OpenAiScreenPrompt {
    val systemPrompt =
        "You are Bucket Money AI, a highly personalized and knowledgeable financial advisor. Your role is to provide tailored financial advice, insights, and overviews based strictly on the user’s provided transactions. Focus on analyzing these transactions to identify spending patterns, potential savings, and budget adjustments. Avoid suggesting external tools or apps unless explicitly requested by the user. Ensure your responses are clear, concise, and actionable, helping users to optimize their spending, identify savings opportunities, and achieve their financial goals while considering their unique financial situation."
    val userPrompt =
        "Attached is a list of my recent transactions. Based strictly on this information, please answer the question provided it is related to financial advice, insights, and overviews or any analysis and questions about the transactions. Here is the question: "
}

@HiltViewModel
class AIAnalysisChatViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val transactionDao: TransactionDao,
    private val ivyContext: IvyWalletCtx,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val TAG = "AiAnalysisChatViewModel"
    val TEMPERATURE = 0.2
    val MAX_TOKENS = 600
    val FREQUENCY_PENALTY = 0.5
    val PRESENCE_PENALTY = 0.5


    private val _chatUiState = MutableStateFlow(ChatUiState())
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _chatHistory = MutableStateFlow<MutableList<ChatMessageEntity>>(mutableListOf())
    val chatHistory: StateFlow<List<ChatMessageEntity>> = _chatHistory.asStateFlow()

    private val _period = MutableStateFlow(ivyContext.selectedPeriod)
    val period = _period.readOnly()


    private val _baseCurrencyCode = MutableStateFlow("")
    val baseCurrencyCode = _baseCurrencyCode.readOnly()

    private val _showCloseButtonOnly = MutableStateFlow(false)
    val showCloseButtonOnly = _showCloseButtonOnly.readOnly()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transaction = _transactions.readOnly()

    private var aiResponseJob: Job? = null


    fun start(
        screen: AIAnalysisChat
    ) {
        if (screen.previousAiAnalysis.isNotNullOrBlank()) {
            _chatHistory.value = mutableListOf(
                ChatMessageEntity(
                    content = screen.previousAiAnalysis,
                    type = ChatMessageType.RECEIVED
                )
            )
        } else {
            resetChat()
        }

        initChatScreen(
            period = ivyContext.selectedPeriod
        )
    }

    private fun initChatScreen(
        period: TimePeriod,
    ) {
        _showCloseButtonOnly.value = false

        load(
            period = period,
        )
    }


    private fun load(
        period: TimePeriod,
    ) {
        TestIdlingResource.increment()

        _period.value = period
        val range = period.toRange(ivyContext.startDayOfMonth)

        viewModelScope.launch {
            val settings = ioThread { settingsDao.findFirst() }
            _baseCurrencyCode.value = settings.currency
            _transactions.value = ioThread {
                history(
                    transactionDao = transactionDao,
                    range = range.toCloseTimeRange()
                )
            }
        }

        TestIdlingResource.decrement()
    }


    fun resetChat() {
        _chatHistory.value = mutableListOf(
            ChatMessageEntity(
                content = "Hi, Bucket Money Ai. I can help you analyze your expenses and income. What would you like to know?",
                type = ChatMessageType.RECEIVED
            )
        )
        _chatUiState.value = ChatUiState()
    }

    fun onSetPeriod(period: TimePeriod) {
        ivyContext.updateSelectedPeriodInMemory(period)
        load(
            period = period,
        )
        resetChat()

    }

    fun nextMonth() {
        val month = period.value.month
        val year = period.value.year ?: dateNowUTC().year
        if (month != null) {
            load(
                period = month.incrementMonthPeriod(ivyContext, 1L, year),
            )
            resetChat()

        }

    }

    fun previousMonth() {
        val month = period.value.month
        val year = period.value.year ?: dateNowUTC().year
        if (month != null) {
            load(
                period = month.incrementMonthPeriod(ivyContext, -1L, year)
            )
            resetChat()

        }
    }

    fun onUserPromptChanged(prompt: String) {
        _chatUiState.value = _chatUiState.value.copy(
            userInput = prompt
        )
    }


    fun getCompletion() {
        aiResponseJob = viewModelScope.launch {
            val userInput = ChatMessageEntity(
                content = _chatUiState.value.userInput,
                type = ChatMessageType.SENT

            ) // must be initialised before loading starts else will cause issue in chat screen when reading last or null value
            _chatHistory.value = _chatHistory.value.apply {
                add(userInput)
            }

            _chatUiState.value = _chatUiState.value.copy(
                loading = true,
                error = "",
                transactionsString = "",
                aiInsights = "",
                userInput = _chatUiState.value.userInput
            )
            Timber.tag(TAG).d("getCompletion: ${_chatUiState.value.transactionsString}")
            val previousMessages: MutableList<ChatMessageEntity> = mutableListOf()

            _transactions.value.forEachIndexed { index, transactionItem ->
                val transactionString = "$index.${transactionItem.toChatGptPrompt()}"
                Timber.tag(TAG).d("transactionString: $transactionString")
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

            if (_chatUiState.value.transactionsString.isNullOrEmpty()) {
                _chatUiState.value = _chatUiState.value.copy(
                    loading = false,
                )
                Timber.tag(TAG).d("No transactions available. return")

                val serverResponse = ChatMessageEntity(
                    content = "No transactions found for selected period (${_period.value.toDisplayLong(ivyContext.startDayOfMonth)}). Please select another period with transactions to get insights.",
                    type = ChatMessageType.RECEIVED
                )

                _chatHistory.value = _chatHistory.value.apply {
                    add(serverResponse)
                }
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


            val extraPromptDetails = "Currency - ${baseCurrencyCode.value}."

            val openAI = OpenAI(Constants.OPEN_AI_API_KEY)
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId("gpt-3.5-turbo"),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = OpenAiScreenPrompt.systemPrompt
                    ),
                    *chatMessageContext.toTypedArray(),
                    ChatMessage(
                        role = ChatRole.User,
                        content = extraPromptDetails + OpenAiScreenPrompt.userPrompt + _chatUiState.value.userInput
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
                            userInput = ""
                        )


                    }
                    context.vibratePhone(
                        amplitude = 10
                    )
                }
//                Timber.tag(TAG).d("Done with completion")
                _chatUiState.value = _chatUiState.value.copy(loading = false, error = "")
                val serverResponse = ChatMessageEntity(
                    content = _chatUiState.value.aiInsights ?: "",
                    type = ChatMessageType.RECEIVED
                )

                _chatHistory.value = _chatHistory.value.apply {
                    add(serverResponse)
                }


            } catch (e: Exception) {
                Timber.tag(TAG).e(e)
                _chatUiState.value = _chatUiState.value.copy(
                    error = "Oops! Bucket Money AI had an issue connecting and can't provide insights right now. Remember, as Warren Buffett said, 'Do not save what is left after spending, but spend what is left after saving.' Stay tuned for updates!",
                    loading = false
                )
                val serverResponse = ChatMessageEntity(
                    content = "There was an issue connecting to the servers.",
                    type = ChatMessageType.RECEIVED
                )

                _chatHistory.value = _chatHistory.value.apply {
                    add(serverResponse)
                }

            }

        }

    }


    fun cancelAiResponseJob() {
        aiResponseJob?.cancel()
    }


}
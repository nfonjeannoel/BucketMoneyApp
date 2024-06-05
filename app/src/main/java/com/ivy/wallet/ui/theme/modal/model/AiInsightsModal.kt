package com.ivy.wallet.ui.theme.modal.model

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.Constants
import com.ivy.wallet.base.*
import com.ivy.wallet.model.TransactionHistoryItem
import com.ivy.wallet.model.entity.Transaction
import com.ivy.wallet.ui.IvyWalletPreview
import com.ivy.wallet.ui.ivyWalletCtx
import com.ivy.wallet.ui.onboarding.model.TimePeriod
import com.ivy.wallet.ui.theme.*
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalStartChat
import com.ivy.wallet.ui.theme.modal.model.Month.Companion.fromMonthValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

data class AiInsightsModalData(
    val id: UUID = UUID.randomUUID(),
    val period: TimePeriod,
    val history: List<TransactionHistoryItem>
)

data class ChatUiState(
    val transactionsString: String? = null,
    val aiInsights: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

object OpenAiPrompt {
    val systemPrompt =
        "You are Bucket Money AI, a helpful money manager that provides personalized financial advice, insights, and overviews based on user transactions."
    val userPrompt =
        "Here is a list of my recent transactions. Based on this information, please provide an analysis of my spending habits, personalized financial advice, and any tips for better money management. Here are the Transactions -> "
}

val TAG = "aiinsightmodel"
val TEMPERATURE = 0.2
val MAX_TOKENS = 600
val FREQUENCY_PENALTY = 0.5
val PRESENCE_PENALTY = 0.5


@Composable
fun BoxWithConstraintsScope.AiInsightsModal(
    modal: AiInsightsModalData?,
    dismiss: () -> Unit,
    onChatClicked: () -> Unit
) {

    var chatUiState by remember {
        mutableStateOf(
            ChatUiState(
                transactionsString = "",
                aiInsights = null,
                loading = true,
                error = null
            )
        )
    }

//    var transactionString = ""
    var hasTransactions = false
    modal?.history?.forEachIndexed { index, transactionHistoryItem ->
        if (transactionHistoryItem is Transaction) {
            hasTransactions = true
            chatUiState = chatUiState.copy(
                transactionsString =chatUiState.transactionsString + "$index.${transactionHistoryItem.toChatGptPrompt()}"
            )
        }
    }

    Timber.tag(TAG).d("transactionsString ${chatUiState.transactionsString}")





    if (hasTransactions) {
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(key1 = true) {
            coroutineScope.launch {

                val openAI = OpenAI(Constants.OPEN_AI_API_KEY)

                val chatMessage = ChatMessage(
                    role = ChatRole.User,
                    content = OpenAiPrompt.userPrompt + chatUiState.transactionsString
                )
                Timber.tag(TAG).d("chatMessage ${chatMessage.content}")
                val chatCompletionRequest = ChatCompletionRequest(
                    model = ModelId("gpt-3.5-turbo"),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = OpenAiPrompt.systemPrompt
                        ),
                        chatMessage

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
                        response?.let {
                            withContext(Dispatchers.Main) {
                                chatUiState = chatUiState.copy(
                                    aiInsights = (chatUiState.aiInsights ?: "") + it,
                                    loading = false,
                                    transactionsString = chatUiState.transactionsString,
                                    error = chatUiState.error
                                )
                            }

                            Timber.tag(TAG).d("response $it")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        chatUiState = chatUiState.copy(loading = false, error = null)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        chatUiState = chatUiState.copy(error = e.message, loading = false)
                    }

                }
            }
        }

    } else {
        chatUiState = chatUiState.copy(
            loading = true,
            error = null,
            transactionsString = chatUiState.transactionsString,
            aiInsights = null
        )
    }


    val ivyContext = ivyWalletCtx()
    val modalScrollState = rememberScrollState()

    IvyModal(
        id = modal?.id,
        visible = modal != null,
        dismiss = dismiss,
        scrollState = modalScrollState,
        PrimaryAction = {
            ModalStartChat(
                // condition to enable set button
                label = "Chat",
                enabled = true
            ) {
                // on set button clicked
                onChatClicked()
                dismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))

//        Timber.tag("aiinsightmodel").d( "period ${period?.year}")

        DisplayAiInsights(
            period = modal?.period,
            chatUiState = chatUiState
        )
//        Spacer(Modifier.height(32.dp))
//
//        IvyDividerLine(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 24.dp)
//        )

        Spacer(Modifier.height(32.dp))

        // for bottom blur
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DisplayAiInsights(
    period: TimePeriod?,
    chatUiState: ChatUiState
) {
    val ivyContext = ivyWalletCtx()
    Text(
        modifier = Modifier
            .padding(start = 32.dp),
        text = "Ai analysis: ${period?.toDisplayLong(ivyContext.startDayOfMonth)}"
            ?: "Selected Period",
        style = UI.typo.b1.style(
            color = if (period != null) UI.colors.pureInverse else Gray,
            fontWeight = FontWeight.ExtraBold
        )
    )

    Spacer(Modifier.height(24.dp))


    Column(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp)
    ) {
        if (chatUiState.loading) {
            // Display a loading indicator
            CircularProgressIndicator()
        } else if (chatUiState.error != null) {
            // Display the error message
            Text(text = chatUiState.error!!)
        } else if (chatUiState.aiInsights != null) {
            // Display the AI insights
            Text(text = chatUiState.aiInsights!!)
        } else if (chatUiState.transactionsString.isNullOrEmpty()) {
            // Display a message when there are no transactions
            Text(text = "No transactions available.")
        }
    }


//
//    val state = rememberLazyListState()
//
//    val coroutineScope = rememberCoroutineScope()
//    onScreenStart {
//    }


}

data class MonthYear(
    val month: Month,
    val year: Int
) {
    fun forDisplay(
        currentYear: Int
    ): String {
        return if (year != currentYear) {
            //not current year
            "${month.name}, $year"
        } else {
            //current year
            month.name
        }
    }
}


@Preview
@Composable
private fun Preview_MonthSelected() {
    IvyWalletPreview {
        AiInsightsModal(
            modal = AiInsightsModalData(
                period = TimePeriod(
                    month = fromMonthValue(3),
                ),
                history = emptyList()
            ),
            dismiss = {},
            onChatClicked = {}
        )
    }
}


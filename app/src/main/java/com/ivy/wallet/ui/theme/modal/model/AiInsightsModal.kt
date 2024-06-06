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
import com.ivy.design.l1_buildingBlocks.data.background
import com.ivy.wallet.Constants
import com.ivy.wallet.base.*
import com.ivy.wallet.model.TransactionHistoryItem
import com.ivy.wallet.model.entity.Transaction
import com.ivy.wallet.ui.IvyWalletPreview
import com.ivy.wallet.ui.home.ChatUiState
import com.ivy.wallet.ui.ivyWalletCtx
import com.ivy.wallet.ui.onboarding.model.TimePeriod
import com.ivy.wallet.ui.theme.*
import com.ivy.wallet.ui.theme.components.IvyDividerLine
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
)

object OpenAiPrompt {
    val systemPrompt =
        "You are Bucket Money AI impersonating a helpful money manager that provides personalized financial advice, insights, and overviews based on user transactions while considering all matrices."
    val userPrompt =
        "That is a list of my recent transactions. Based on this information, please provide an analysis of my spending habits, personalized financial advice."
}

val TAG = "aiinsightmodel"


@Composable
fun BoxWithConstraintsScope.AiInsightsModal(
    modal: AiInsightsModalData?,
    chatUiState: ChatUiState,
    dismiss: () -> Unit,
    onChatClicked: () -> Unit
) {


    val modalScrollState = rememberScrollState()

    IvyModal(
        id = modal?.id,
        visible = modal != null,
        dismiss = dismiss,
        scrollState = modalScrollState,
        PrimaryAction = {
            if (chatUiState.loading) {
                // Display a loading indicator
                CircularProgressIndicator()
            } else {
                ModalStartChat(
                    // condition to enable set button
                    label = "Chat",
                    enabled = chatUiState.aiInsights.isNotNullOrBlank(),
                ) {
                    // on set button clicked
                    onChatClicked()
                    dismiss()
                }
            }

        }
    ) {
        Spacer(Modifier.height(32.dp))


        DisplayAiInsights(
            period = modal?.period,
            chatUiState = chatUiState
        )

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
        text = "AI Analysis: ${period?.toDisplayLong(ivyContext.startDayOfMonth)}"
            ?: "Selected Period",
        style = UI.typo.b1.style(
            color = if (period != null) UI.colors.pureInverse else Gray,
            fontWeight = FontWeight.ExtraBold
        )
    )
    IvyDividerLine()

    Spacer(Modifier.height(12.dp))


    Column(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp)
    ) {
        if (chatUiState.error != null) {
            // Display the error message
            Text(text = chatUiState.error)
        }
        if (chatUiState.aiInsights != null) {
            // Display the AI insights
            Text(text = chatUiState.aiInsights)
        }
        if (chatUiState.transactionsString.isNullOrEmpty()) {
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

            ),
            chatUiState = ChatUiState(),
            dismiss = {},
            onChatClicked = {}
        )
    }
}


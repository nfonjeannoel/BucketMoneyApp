package com.ivy.wallet.ui.theme.modal.model

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
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
import com.ivy.wallet.ui.theme.modal.ModalLoadingButton
import com.ivy.wallet.ui.theme.modal.ModalStartChat
import com.ivy.wallet.ui.theme.modal.model.Month.Companion.fromMonthValue
import dev.jeziellago.compose.markdowntext.MarkdownText
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
        "You are Bucket Money AI, a highly personalized and knowledgeable financial advisor. Your role is to provide tailored financial advice, insights, and overviews based strictly on the user’s provided transactions. Focus on analyzing these transactions to identify spending patterns, potential savings, and budget adjustments. Avoid suggesting external tools or apps unless explicitly requested by the user. Ensure your responses are clear, concise, and actionable, helping users to optimize their spending, identify savings opportunities, and achieve their financial goals while considering their unique financial situation."
    val userPrompt =
        "Attached is a list of my recent transactions. Based strictly on this information, please analyze my spending habits and provide personalized financial advice. Specifically, identify areas where I can save money, suggest budget adjustments, and highlight any unusual spending patterns. Additionally, offer practical tips and strategies to help me achieve my financial goals, such as saving for a major purchase or reducing debt, but do not recommend any external tools or apps unless I explicitly ask for such recommendations. It is not necessary to address each transaction individually. However, you can do so to some which you have comments on"
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
                ModalLoadingButton(){
                    CircularProgressIndicator(
                        color = White,
                    )
                }
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
//            Text(text = chatUiState.aiInsights)
            MarkdownText(
                markdown =chatUiState.aiInsights,
                style = LocalTextStyle.current
                    .style(UI.colors.pureInverse),

            )
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


package com.ivy.wallet.ui.theme.modal.model

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.R
import com.ivy.wallet.base.*
import com.ivy.wallet.model.IntervalType
import com.ivy.wallet.ui.IvyWalletPreview
import com.ivy.wallet.ui.ivyWalletCtx
import com.ivy.wallet.ui.onboarding.model.FromToTimeRange
import com.ivy.wallet.ui.onboarding.model.LastNTimeRange
import com.ivy.wallet.ui.onboarding.model.TimePeriod
import com.ivy.wallet.ui.theme.*
import com.ivy.wallet.ui.theme.components.CircleButtonFilled
import com.ivy.wallet.ui.theme.components.IntervalPickerRow
import com.ivy.wallet.ui.theme.components.IvyDividerLine
import com.ivy.wallet.ui.theme.modal.ChoosePeriodModal
import com.ivy.wallet.ui.theme.modal.ChoosePeriodModalData
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalSet
import com.ivy.wallet.ui.theme.modal.ModalStartChat
import com.ivy.wallet.ui.theme.modal.model.Month
import com.ivy.wallet.ui.theme.modal.model.Month.Companion.fromMonthValue
import com.ivy.wallet.ui.theme.modal.model.Month.Companion.monthsList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.util.*

data class AiInsightsModalData(
    val id: UUID = UUID.randomUUID(),
    val period: TimePeriod
)

@Composable
fun BoxWithConstraintsScope.AiInsightsModal(
    modal: AiInsightsModalData?,
    dismiss: () -> Unit,
    onChatClicked: () -> Unit
) {
    val period by remember(modal) {
        mutableStateOf(modal?.period)
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
            period = period
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
) {
    val ivyContext = ivyWalletCtx()
    Text(
        modifier = Modifier
            .padding(start = 32.dp),
        text = "Ai analysis: ${period?.toDisplayLong(ivyContext.startDayOfMonth)}" ?: "Selected Period",
        style = UI.typo.b1.style(
            color = if (period != null) UI.colors.pureInverse else Gray,
            fontWeight = FontWeight.ExtraBold
        )
    )

    Spacer(Modifier.height(24.dp))


    val state = rememberLazyListState()

    val coroutineScope = rememberCoroutineScope()
    onScreenStart {
    }


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
                    month = fromMonthValue(3)
                )
            ),
            dismiss = {},
            onChatClicked = {}
        )
    }
}


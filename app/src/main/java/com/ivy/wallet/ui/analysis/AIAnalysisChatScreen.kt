package com.ivy.wallet.ui.analysis

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.ivy.design.api.navigation
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.R
import com.ivy.wallet.base.*
import com.ivy.wallet.model.TransactionType
import com.ivy.wallet.model.entity.Category
import com.ivy.wallet.model.entity.Transaction
import com.ivy.wallet.ui.*
import com.ivy.wallet.ui.home.ChatUiState
import com.ivy.wallet.ui.onboarding.model.TimePeriod
import com.ivy.wallet.ui.statistic.level1.CategoryAmount
import com.ivy.wallet.ui.statistic.level1.PieChart
import com.ivy.wallet.ui.statistic.level1.PieChartStatisticViewModel
import com.ivy.wallet.ui.statistic.level1.SelectedCategory
import com.ivy.wallet.ui.theme.*
import com.ivy.wallet.ui.theme.components.*
import com.ivy.wallet.ui.theme.modal.ChoosePeriodModal
import com.ivy.wallet.ui.theme.modal.ChoosePeriodModalData
import com.ivy.wallet.ui.theme.wallet.AmountCurrencyB1Row
import java.util.*

@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.AIAnalysisChatScreen(
    screen: AIAnalysisChat
) {
    val viewModel: AIAnalysisChatViewModel = viewModel()

    val ivyContext = ivyWalletCtx()

    val period by viewModel.period.collectAsState()
    val showCloseButtonOnly by viewModel.showCloseButtonOnly.collectAsState()
    val chatUiState by viewModel.chatUiState.collectAsState()

    onScreenStart {
        viewModel.start(screen)
    }

    UI(
        chatUiState = chatUiState,
        period = period,
        showCloseButtonOnly = showCloseButtonOnly,
        onSetPeriod = viewModel::onSetPeriod,
        onSelectNextMonth = viewModel::nextMonth,
        onSelectPreviousMonth = viewModel::previousMonth,
        onResetChat = viewModel::resetChat
    )
}

@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
    chatUiState: ChatUiState = ChatUiState(),
    period: TimePeriod,
    showCloseButtonOnly: Boolean = false,

    onSelectNextMonth: () -> Unit = {},
    onSelectPreviousMonth: () -> Unit = {},
    onSetPeriod: (TimePeriod) -> Unit = {},
    onResetChat: () -> Unit = {}
) {
    var choosePeriodModal: ChoosePeriodModalData? by remember {
        mutableStateOf(null)
    }

    val lazyState = rememberLazyListState()

    val nav = navigation()
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        state = lazyState
    ) {
        stickyHeader {
            Header(
                period = period,
                onShowMonthModal = {
                    choosePeriodModal = ChoosePeriodModalData(
                        period = period
                    )
                },
                onSelectNextMonth = onSelectNextMonth,
                onSelectPreviousMonth = onSelectPreviousMonth,
                showCloseButtonOnly = showCloseButtonOnly,

                onClose = {
                    nav.back()
                },
                onResetChat = {
                    onResetChat()
                    Toast.makeText(context, "Chat reset", Toast.LENGTH_LONG).show()
                }
            )
        }

        item {
            Spacer(Modifier.height(20.dp))

            Text(
                modifier = Modifier.padding(start = 32.dp),
                text = chatUiState.aiInsights ?: "AI Analysis",
                style = UI.typo.b1.style(
                    fontWeight = FontWeight.ExtraBold
                )
            )
        }




        item {
            Spacer(Modifier.height(160.dp)) //scroll hack
        }
    }

    ChoosePeriodModal(
        modal = choosePeriodModal,
        dismiss = {
            choosePeriodModal = null
        }
    ) {
        onSetPeriod(it)
    }
}

private fun selectCategory(
    categoryToSelect: Category?,

    setSelectedCategory: (SelectedCategory?) -> Unit
) {
    setSelectedCategory(
        SelectedCategory(
            category = categoryToSelect
        )
    )
}

@Composable
private fun Header(
    period: TimePeriod,

    showCloseButtonOnly: Boolean = false,


    onShowMonthModal: () -> Unit,
    onSelectNextMonth: () -> Unit,
    onSelectPreviousMonth: () -> Unit,

    onClose: () -> Unit,
    onResetChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(pureBlur())
            .statusBarsPadding()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(20.dp))

        CloseButton {
            onClose()
        }

        if (!showCloseButtonOnly) {
            Spacer(Modifier.weight(1f))

            IvyOutlinedButton(
                modifier = Modifier.horizontalSwipeListener(
                    sensitivity = 75,
                    onSwipeLeft = {
                        onSelectNextMonth()
                    },
                    onSwipeRight = {
                        onSelectPreviousMonth()
                    }
                ),
                iconStart = R.drawable.ic_calendar,
                text = period.toDisplayShort(ivyWalletCtx().startDayOfMonth),
            ) {
                onShowMonthModal()
            }

            Spacer(Modifier.width(12.dp))

            val backgroundGradient = GradientGreen
            CircleButtonFilledGradient(
                iconPadding = 4.dp,
                icon = R.drawable.ic_plus,
                backgroundGradient = backgroundGradient,
                tint = White,
            ) {
                onResetChat()
            }

            Spacer(Modifier.width(20.dp))
        }

    }
}


@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_Expense() {
    IvyWalletPreview {
        UI(
            period = TimePeriod.currentMonth(
                startDayOfMonth = 1
            ),
        )
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_Income() {
    IvyWalletPreview {
        UI(
            period = TimePeriod.currentMonth(
                startDayOfMonth = 1
            )
        )
    }
}


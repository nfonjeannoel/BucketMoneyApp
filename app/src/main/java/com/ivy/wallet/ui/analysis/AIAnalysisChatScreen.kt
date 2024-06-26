package com.ivy.wallet.ui.analysis

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aallam.openai.client.Chat
import com.google.accompanist.insets.WindowInsets
import com.google.accompanist.insets.imePadding
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.navigationBarsWithImePadding
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
import com.ivy.wallet.ui.home.ChatMessageEntity
import com.ivy.wallet.ui.home.ChatMessageType
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
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val chatHistory by viewModel.chatHistory.collectAsState()


    onScreenStart {
        viewModel.start(screen)
    }

    UI(
        chatUiState = chatUiState,
        period = period,
        showCloseButtonOnly = showCloseButtonOnly,
        chatHistory = chatHistory,
        onSetPeriod = viewModel::onSetPeriod,
        onSelectNextMonth = viewModel::nextMonth,
        onSelectPreviousMonth = viewModel::previousMonth,
        onResetChat = viewModel::resetChat,
        getCompletion = viewModel::getCompletion,
        onUserInputChanged = viewModel::onUserPromptChanged,
        onCancelAiResponseJob = viewModel::cancelAiResponseJob
    )
}

@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
    chatUiState: ChatUiState = ChatUiState(),
    period: TimePeriod,
    showCloseButtonOnly: Boolean = false,
    chatHistory: List<ChatMessageEntity> = emptyList(),

    onSelectNextMonth: () -> Unit = {},
    onSelectPreviousMonth: () -> Unit = {},
    onSetPeriod: (TimePeriod) -> Unit = {},
    onResetChat: () -> Unit = {},
    getCompletion: () -> Unit = {},
    onUserInputChanged: (String) -> Unit = {},
    onCancelAiResponseJob: () -> Unit = {},
) {
    var choosePeriodModal: ChoosePeriodModalData? by remember {
        mutableStateOf(null)
    }

    val scrollState = rememberScrollState()


    val nav = navigation()
    val context = LocalContext.current


    Scaffold() { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .clip(
                    RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                    )
                )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {

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
                        onCancelAiResponseJob()
                        nav.back()
                    },
                    onResetChat = {
                        onResetChat()
                    }
                )

                Column(
                    Modifier
                        .weight(1f, fill = true)
                        .fillMaxSize()

                ) {

                    MessagesSection(
                        chatUiState = chatUiState,
                        chatHistory = chatHistory
                    )
                }
//
//                val relocation = remember { BringIntoViewRequester() }
//                val scope = rememberCoroutineScope()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(4.dp)
                ) {
                    OutlinedTextField(
                        value = chatUiState.userInput,
                        onValueChange = {
                            onUserInputChanged(it)
                        },
                        keyboardOptions = KeyboardOptions(

                        ),
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .padding(start = 16.dp, end = 16.dp)
                            .navigationBarsWithImePadding()
//                            .bringIntoViewRequester(relocation)
//                            .onFocusEvent {
//                                if (it.isFocused) scope.launch { delay(300); relocation.bringIntoView() }
//                            }
                        ,
                        shape = MaterialTheme.shapes.medium,
                        maxLines = 4,
                        enabled = !chatUiState.loading,
                        placeholder = {
                            Text(
                                text = "Type a message",
                                color = MaterialTheme.colors.onSurface
                            )
                        },
                        trailingIcon = {
                            if (chatUiState.userInput.trim().isEmpty()) {
                                // do nothing
                            } else if (chatUiState.loading) {
                                CircularProgressIndicator()
                            } else {
                                IconButton(onClick = {
                                    getCompletion()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowCircleUp,
                                        contentDescription = "Send",
                                        tint = MaterialTheme.colors.onSurface,
                                        modifier = Modifier
                                            .size(32.dp)
                                    )
                                }
                            }
                        }


                    )

                }


            }
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

//@Composable
//private fun userPromptSection(
//    chatUiState: ChatUiState,
//    onUserInputChanged: (String) -> Unit,
//    getCompletion: () -> Unit
//) {
//
//    Row(
//        verticalAlignment = Alignment.CenterVertically,
//        modifier = Modifier
//            .padding(4.dp)
//            .imePadding()
//    ) {
//
//
//
//    }
//}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MessagesSection(
    chatUiState: ChatUiState,
    chatHistory: List<ChatMessageEntity>
) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            // Start the animation immediately.
            targetState = true
        }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
        ),
        exit = fadeOut(),
    ) {
        LazyColumn(

        ) {
            itemsIndexed(chatHistory) { index, chatMessage ->
                MessageCard(
                    chatUiState = chatUiState,
                    chatMessage = chatMessage,
                    shouldStream = true,
                    modifier = Modifier
                        // Animate each list item to slide in vertically
                        .animateEnterExit(
                            enter = slideInVertically()
                        )
                )
            }

            item {
                val chatMessage = chatHistory.lastOrNull()
                if (chatUiState.loading) {
                    Card(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(0.dp),
                        border = null,
                        modifier = Modifier
                            // Animate each list item to slide in vertically
                            .animateEnterExit(
                                enter = slideInVertically()
                            )
//            .padding(8.dp)
                            .fillMaxWidth()
                            .displayCutoutPadding()
//            .clip(MaterialTheme.shapes.small)
                            .background(White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Image(
                                painter = painterResource(
                                    id = R.drawable.bm_ai
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Top)
                            )

                            Column(
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "Bucket Money Ai",
                                    style = TextStyle(
                                        color = Black,
                                        fontSize = 14.sp,
                                    ),
                                    fontWeight = FontWeight.Bold,
                                )
//                Divider()
                                MarkdownText(
                                    markdown =chatUiState.aiInsights ?: "...",
                                    style = LocalTextStyle.current
                                        .style(UI.colors.pureInverse),

                                    )
//                                Text(text = chatUiState.aiInsights ?: "...")


                            }
                        }


                    }
                }


            }
        }

    }
}

@Composable
fun MessageCard(
    chatUiState: ChatUiState,
    chatMessage: ChatMessageEntity,
    shouldStream: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = 2.dp,
        shape = RoundedCornerShape(0.dp),
        border = null,
        modifier = modifier
//            .padding(8.dp)
            .fillMaxWidth()
            .displayCutoutPadding()
//            .clip(MaterialTheme.shapes.small)
            .background(White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Image(
                painter = painterResource(
                    id = when (chatMessage.type) {
                        ChatMessageType.SENT -> R.drawable.person
                        ChatMessageType.RECEIVED -> R.drawable.bm_ai
                    }
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Top)
            )

            Column(
                modifier = Modifier.padding(start = 8.dp)
            ) {

                Text(
                    text = when (chatMessage.type) {
                        ChatMessageType.SENT -> "You"
                        ChatMessageType.RECEIVED -> "Bucket Money Ai"
                    },
                    style = TextStyle(
                        color = Black,
                        fontSize = 14.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                )

                MarkdownText(
                    markdown =chatMessage.content,
                    style = LocalTextStyle.current
                        .style(UI.colors.pureInverse),

                    )
//                Divider()
//                Text(text = chatMessage.content)


            }
        }


    }
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


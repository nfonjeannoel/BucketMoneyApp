package com.ivy.wallet.ui.accounts

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.ivy.wallet.base.clickableNoIndication
import com.ivy.wallet.base.format
import com.ivy.wallet.base.horizontalSwipeListener
import com.ivy.wallet.base.onScreenStart
import com.ivy.wallet.model.entity.Account
import com.ivy.wallet.ui.ItemStatistic
import com.ivy.wallet.ui.IvyWalletPreview
import com.ivy.wallet.ui.Main
import com.ivy.wallet.ui.ivyWalletCtx
import com.ivy.wallet.ui.main.MainTab
import com.ivy.wallet.ui.theme.*
import com.ivy.wallet.ui.theme.components.*
import com.ivy.wallet.ui.theme.modal.edit.AccountModal
import com.ivy.wallet.ui.theme.modal.edit.AccountModalData

@Composable
fun BoxWithConstraintsScope.AccountsTab(screen: Main) {
    val viewModel: AccountsViewModel = viewModel()

    val baseCurrency by viewModel.baseCurrencyCode.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val totalBalanceWithExcluded by viewModel.totalBalanceWithExcluded.collectAsState()

    onScreenStart {
        viewModel.start()
    }

    UI(
        baseCurrency = baseCurrency,
        accounts = accounts,
        totalBalanceWithExcluded = totalBalanceWithExcluded,

        onReorder = viewModel::reorder,
        onEditAccount = viewModel::editAccount,
    )
}

@Composable
private fun BoxWithConstraintsScope.UI(
    baseCurrency: String,
    accounts: List<AccountData>,
    totalBalanceWithExcluded: Double?,

    onReorder: (List<AccountData>) -> Unit,
    onEditAccount: (Account, Double) -> Unit,
) {
    var reorderVisible by remember { mutableStateOf(false) }
    var accountModalData: AccountModalData? by remember { mutableStateOf(null) }

    val ivyContext = ivyWalletCtx()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .horizontalSwipeListener(
                sensitivity = 200,
                onSwipeLeft = {
                    ivyContext.selectMainTab(MainTab.HOME)
                },
                onSwipeRight = {
                    ivyContext.selectMainTab(MainTab.HOME)
                }
            ),
    ) {
        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(24.dp))

            Column {
                Text(
                    text = "Accounts",
                    style = UI.typo.b1.style(
                        color = UI.colors.pureInverse,
                        fontWeight = FontWeight.ExtraBold
                    )
                )

                if (totalBalanceWithExcluded != null) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Total: $baseCurrency ${
                            totalBalanceWithExcluded.format(
                                baseCurrency
                            )
                        }",
                        style = UI.typo.nB2.style(
                            color = Gray,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }


            Spacer(Modifier.weight(1f))

            ReorderButton {
                reorderVisible = true
            }

            Spacer(Modifier.width(24.dp))
        }


        Spacer(Modifier.height(16.dp))

        val nav = navigation()
        for (accountData in accounts) {
            AccountCard(
                baseCurrency = baseCurrency,
                accountData = accountData,
                onBalanceClick = {
                    nav.navigateTo(
                        ItemStatistic(
                            accountId = accountData.account.id,
                            categoryId = null
                        )
                    )
                },
                onLongClick = {
                    reorderVisible = true
                }
            ) {
                nav.navigateTo(
                    ItemStatistic(
                        accountId = accountData.account.id,
                        categoryId = null
                    )
                )
            }
        }

        Spacer(Modifier.height(150.dp)) //scroll hack
    }

    ReorderModalSingleType(
        visible = reorderVisible,
        initialItems = accounts,
        dismiss = {
            reorderVisible = false
        },
        onReordered = onReorder
    ) { _, item ->
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 24.dp)
                .padding(vertical = 8.dp),
            text = item.account.name,
            style = UI.typo.b1.style(
                color = item.account.color.toComposeColor(),
                fontWeight = FontWeight.Bold
            )
        )
    }

    AccountModal(
        modal = accountModalData,
        onCreateAccount = { },
        onEditAccount = onEditAccount,
        dismiss = {
            accountModalData = null
        }
    )
}

@Composable
private fun AccountCard(
    baseCurrency: String,
    accountData: AccountData,
    onBalanceClick: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val account = accountData.account
    val contrastColor = findContrastTextColor(account.color.toComposeColor())

    Spacer(Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .clickable(
                onClick = onClick
            )
    ) {
        val currency = account.currency ?: baseCurrency

        AccountHeader(
            accountData = accountData,
            currency = currency,
            baseCurrency = baseCurrency,
            contrastColor = contrastColor,

            onBalanceClick = onBalanceClick
        )

        Spacer(Modifier.height(12.dp))

        IncomeExpensesRow(
            currency = currency,
            incomeLabel = "INCOME THIS MONTH",
            income = accountData.monthlyIncome,
            expensesLabel = "EXPENSES THIS MONTH",
            expenses = accountData.monthlyExpenses
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountHeader(
    accountData: AccountData,
    currency: String,
    baseCurrency: String,
    contrastColor: Color,

    onBalanceClick: () -> Unit
) {
    val account = accountData.account

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(account.color.toComposeColor(), UI.shapes.r4Top)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(20.dp))

            ItemIconSDefaultIcon(
                iconName = account.icon,
                defaultIcon = R.drawable.ic_custom_account_s,
                tint = contrastColor
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = account.name,
                style = UI.typo.b1.style(
                    color = contrastColor,
                    fontWeight = FontWeight.ExtraBold
                )
            )

            if (!account.includeInBalance) {
                Spacer(Modifier.width(8.dp))

                Text(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 4.dp),
                    text = "(excluded)",
                    style = UI.typo.c.style(
                        color = account.color.toComposeColor().dynamicContrast()
                    )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        BalanceRow(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickableNoIndication {
                    onBalanceClick()
                },
            decimalPaddingTop = 7.dp,
            spacerDecimal = 6.dp,
            textColor = contrastColor,
            currency = currency,
            balance = accountData.balance,

            integerFontSize = 30.sp,
            decimalFontSize = 18.sp,
            currencyFontSize = 30.sp,

            currencyUpfront = false
        )

        if (currency != baseCurrency && accountData.balanceBaseCurrency != null) {
            BalanceRowMini(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickableNoIndication {
                        onBalanceClick()
                    }
                    .testTag("baseCurrencyEquivalent"),
                textColor = account.color.toComposeColor().dynamicContrast(),
                currency = baseCurrency,
                balance = accountData.balanceBaseCurrency,
                currencyUpfront = false
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Preview
@Composable
private fun PreviewAccountsTab() {
    IvyWalletPreview {
        UI(
            baseCurrency = "BGN",
            accounts = listOf(
                AccountData(
                    account = Account("Phyre", color = Green.toArgb()),
                    balance = 2125.0,
                    balanceBaseCurrency = null,
                    monthlyExpenses = 920.0,
                    monthlyIncome = 3045.0
                ),
                AccountData(
                    account = Account("DSK", color = GreenLight.toArgb()),
                    balance = 12125.21,
                    balanceBaseCurrency = null,
                    monthlyExpenses = 1350.50,
                    monthlyIncome = 8000.48
                ),
                AccountData(
                    account = Account(
                        "Revolut",
                        color = IvyDark.toArgb(),
                        currency = "USD",
                        icon = "revolut",
                        includeInBalance = false
                    ),
                    balance = 1200.0,
                    balanceBaseCurrency = 1979.64,
                    monthlyExpenses = 750.0,
                    monthlyIncome = 1000.30
                ),
                AccountData(
                    account = Account(
                        "Cash",
                        color = GreenDark.toArgb(),
                        icon = "cash"
                    ),
                    balance = 820.0,
                    balanceBaseCurrency = null,
                    monthlyExpenses = 340.0,
                    monthlyIncome = 400.0
                ),
            ),
            totalBalanceWithExcluded = 25.54,

            onReorder = {},
            onEditAccount = { _, _ -> }
        )
    }
}
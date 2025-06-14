package com.ivy.wallet.ui.theme.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.base.dateNowUTC
import com.ivy.wallet.base.format
import com.ivy.wallet.base.formatLocal
import com.ivy.wallet.ui.IvyWalletComponentPreview
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import java.time.LocalDate

@Composable
fun HistoryDateDivider(
    date: LocalDate,
    spacerTop: Dp,
    baseCurrency: String,
    income: Double,
    expenses: Double
) {
    Spacer(Modifier.height(spacerTop))

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(24.dp))

        val today = dateNowUTC()

        Column {
            Text(
                text = date.formatLocal(
                    if (today.year == date.year) "MMMM dd." else "MMM dd. yyy"
                ),
                style = UI.typo.b1.style(
                    fontWeight = FontWeight.ExtraBold
                )
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = when (date) {
                    today -> {
                        "Today"
                    }
                    today.minusDays(1) -> {
                        "Yesterday"
                    }
                    today.plusDays(1) -> {
                        "Tomorrow"
                    }
                    else -> {
                        date.formatLocal("EEEE")
                    }
                },
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(Modifier.weight(1f))


        val cashflow = income - expenses
        Text(
            text = "${cashflow.format(baseCurrency)} $baseCurrency",
            style = UI.typo.nB2.style(
                fontWeight = FontWeight.Bold,
                color = if (cashflow > 0) Green else Gray
            )
        )

        Spacer(Modifier.width(32.dp))
    }

    Spacer(Modifier.height(4.dp))
}

@Preview
@Composable
private fun Preview_Today() {
    IvyWalletComponentPreview {
        HistoryDateDivider(
            date = dateNowUTC(),
            spacerTop = 32.dp,
            baseCurrency = "BGN",
            income = 13.50,
            expenses = 256.13
        )
    }
}

@Preview
@Composable
private fun Preview_Yesterday() {
    IvyWalletComponentPreview {
        HistoryDateDivider(
            date = dateNowUTC().minusDays(1),
            spacerTop = 32.dp,
            baseCurrency = "BGN",
            income = 13.50,
            expenses = 256.13
        )
    }
}

@Preview
@Composable
private fun Preview_OneYear_Ago() {
    IvyWalletComponentPreview {
        HistoryDateDivider(
            date = dateNowUTC().minusYears(1),
            spacerTop = 32.dp,
            baseCurrency = "BGN",
            income = 13.50,
            expenses = 256.13
        )
    }
}
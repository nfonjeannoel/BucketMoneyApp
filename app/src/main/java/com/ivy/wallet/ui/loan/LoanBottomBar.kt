package com.ivy.wallet.ui.loan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivy.wallet.R
import com.ivy.wallet.ui.IvyWalletPreview
import com.ivy.wallet.ui.theme.Blue
import com.ivy.wallet.ui.theme.components.BackBottomBar
import com.ivy.wallet.ui.theme.components.BmButton

@Composable
internal fun BoxWithConstraintsScope.LoanBottomBar(
    onClose: () -> Unit,
    onAdd: () -> Unit
) {
    BackBottomBar(onBack = onClose) {
        BmButton(
            text = "Add loan",
            iconStart = R.drawable.ic_plus
        ) {
            onAdd()
        }
    }
}

@Preview
@Composable
private fun PreviewBottomBar() {
    IvyWalletPreview {
        Column(
            Modifier
                .fillMaxSize()
                .background(Blue)
        ) {

        }

        LoanBottomBar(
            onAdd = {},
            onClose = {}
        )
    }
}
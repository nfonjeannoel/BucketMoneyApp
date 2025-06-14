package com.ivy.wallet.ui.theme.modal

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.base.selectEndTextFieldValue
import com.ivy.wallet.ui.IvyWalletPreview

import com.ivy.wallet.ui.theme.components.BmTitleTextField
import java.util.*

@Composable
fun BoxWithConstraintsScope.NameModal(
    visible: Boolean,
    name: String,
    dismiss: () -> Unit,
    id: UUID = UUID.randomUUID(),
    onNameChanged: (String) -> Unit
) {
    var modalName by remember(id) { mutableStateOf(selectEndTextFieldValue(name)) }

    IvyModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            ModalSave {
                onNameChanged(modalName.text)
                dismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            modifier = Modifier.padding(start = 32.dp),
            text = "Edit name",
            style = UI.typo.b1.style(
                fontWeight = FontWeight.ExtraBold,
                color = UI.colors.pureInverse
            )
        )

        Spacer(Modifier.height(32.dp))

        BmTitleTextField(
            modifier = Modifier.padding(horizontal = 32.dp),
            dividerModifier = Modifier.padding(horizontal = 24.dp),
            value = modalName,
            hint = "What's your name?"
        ) {
            modalName = it
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        NameModal(
            visible = true,
            name = "Iliyan",
            dismiss = {}
        ) {

        }
    }
}
package com.ivy.wallet.logic.loantrasactions

import com.ivy.wallet.base.computationThread
import com.ivy.wallet.model.entity.Transaction

data class LoanTransactionsLogic(
    val Loan: LTLoanMapper,
    val LoanRecord: LTLoanRecordMapper
) {
    suspend fun updateAssociatedLoanData(
        transaction: Transaction?,
        onBackgroundProcessingStart: suspend () -> Unit = {},
        onBackgroundProcessingEnd: suspend () -> Unit = {},
        accountsChanged: Boolean = true
    ) {
        computationThread {

            if (transaction == null)
                return@computationThread

            if (transaction.loanId != null && transaction.loanRecordId == null) {
                Loan.updateAssociatedLoan(
                    transaction = transaction,
                    onBackgroundProcessingStart = onBackgroundProcessingStart,
                    onBackgroundProcessingEnd = onBackgroundProcessingEnd,
                    accountsChanged = accountsChanged
                )
            } else if (transaction.loanId != null && transaction.loanRecordId != null) {
                LoanRecord.updateAssociatedLoanRecord(
                    transaction = transaction,
                    onBackgroundProcessingStart = onBackgroundProcessingStart,
                    onBackgroundProcessingEnd = onBackgroundProcessingEnd
                )
            }
        }
    }
}

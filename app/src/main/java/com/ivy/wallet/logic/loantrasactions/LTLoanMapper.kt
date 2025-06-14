package com.ivy.wallet.logic.loantrasactions

import com.ivy.wallet.base.computationThread
import com.ivy.wallet.base.scopedIOThread
import com.ivy.wallet.logic.model.CreateLoanData
import com.ivy.wallet.model.LoanType
import com.ivy.wallet.model.TransactionType
import com.ivy.wallet.model.entity.Account
import com.ivy.wallet.model.entity.Loan
import com.ivy.wallet.model.entity.LoanRecord
import com.ivy.wallet.model.entity.Transaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.*

class LTLoanMapper(
    private val ltCore: LoanTransactionsCore
) {

    suspend fun createAssociatedLoanTransaction(data: CreateLoanData, loanId: UUID) {
        computationThread {
            ltCore.updateAssociatedTransaction(
                createTransaction = data.createLoanTransaction,
                loanId = loanId,
                amount = data.amount,
                loanType = data.type,
                selectedAccountId = data.account?.id,
                title = data.name,
                isLoanRecord = false,
            )
        }
    }

    suspend fun editAssociatedLoanTransaction(
        loan: Loan,
        createLoanTransaction: Boolean = false,
        transaction: Transaction?
    ) {
        computationThread {
            ltCore.updateAssociatedTransaction(
                createTransaction = createLoanTransaction,
                loanId = loan.id,
                amount = loan.amount,
                loanType = loan.type,
                selectedAccountId = loan.accountId,
                title = loan.name,
                isLoanRecord = false,
                transaction = transaction,
                time = transaction?.dateTime
            )
        }
    }

    suspend fun deleteAssociatedLoanTransactions(loanId: UUID) {
        ltCore.deleteAssociatedTransactions(loanId = loanId)
    }

    suspend fun recalculateLoanRecords(
        oldLoanAccountId: UUID?,
        newLoanAccountId: UUID?,
        loanId: UUID
    ) {
        val accounts = ltCore.fetchAccounts()
        computationThread {

            if (oldLoanAccountId == newLoanAccountId || oldLoanAccountId.fetchAssociatedCurrencyCode(
                    accounts
                ) == newLoanAccountId.fetchAssociatedCurrencyCode(accounts)
            )
                return@computationThread

            val newLoanRecords = calculateLoanRecords(
                loanId = loanId,
                newAccountId = newLoanAccountId,
            )

            ltCore.saveLoanRecords(newLoanRecords)
        }
    }

    suspend fun updateAssociatedLoan(
        transaction: Transaction?,
        onBackgroundProcessingStart: suspend () -> Unit = {},
        onBackgroundProcessingEnd: suspend () -> Unit = {},
        accountsChanged: Boolean = true
    ) {
        computationThread {
            transaction?.loanId ?: return@computationThread

            onBackgroundProcessingStart()

            val loan = ltCore.fetchLoan(transaction.loanId) ?: return@computationThread

            if (accountsChanged) {
                val newLoanRecords: List<LoanRecord> = calculateLoanRecords(
                    loanId = transaction.loanId,
                    newAccountId = transaction.accountId
                )
                ltCore.saveLoanRecords(newLoanRecords)
            }

            val modifiedLoan = loan.copy(
                amount = transaction.amount,
                name = if (transaction.title.isNullOrEmpty()) loan.name else transaction.title,
                type = if (transaction.type == TransactionType.INCOME) LoanType.BORROW else LoanType.LEND,
                accountId = transaction.accountId
            )

            ltCore.saveLoan(modifiedLoan)
        }
        onBackgroundProcessingEnd()
    }

    private suspend fun calculateLoanRecords(
        newAccountId: UUID?,
        loanId: UUID
    ): List<LoanRecord> {
        return scopedIOThread { scope ->
            val loanRecords =
                ltCore.fetchAllLoanRecords(loanId = loanId).map { loanRecord ->
                    scope.async {
                        val convertedAmount: Double? =
                            ltCore.computeConvertedAmount(
                                oldLoanRecordAccountId = loanRecord.accountId,
                                oldLonRecordConvertedAmount = loanRecord.convertedAmount,
                                oldLoanRecordAmount = loanRecord.amount,
                                newLoanRecordAccountID = loanRecord.accountId,
                                newLoanRecordAmount = loanRecord.amount,
                                loanAccountId = newAccountId,
                                accounts = ltCore.fetchAccounts(),
                            )
                        loanRecord.copy(convertedAmount = convertedAmount)
                    }
                }.awaitAll()
            loanRecords
        }
    }

    private suspend fun UUID?.fetchAssociatedCurrencyCode(accountsList: List<Account>): String {
        return ltCore.findAccount(accountsList, this)?.currency ?: ltCore.baseCurrency()
    }
}

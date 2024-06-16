package com.ivy.wallet.ui.onboarding

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.ivy.wallet.base.OpResult
import com.ivy.wallet.base.onScreenStart
import com.ivy.wallet.logic.model.CreateAccountData
import com.ivy.wallet.logic.model.CreateCategoryData
import com.ivy.wallet.model.IvyCurrency
import com.ivy.wallet.model.entity.Account
import com.ivy.wallet.model.entity.Category
import com.ivy.wallet.ui.IvyWalletPreview
import com.ivy.wallet.ui.Onboarding
import com.ivy.wallet.ui.onboarding.model.AccountBalance
import com.ivy.wallet.ui.onboarding.steps.*
import com.ivy.wallet.ui.onboarding.viewmodel.OnboardingViewModel


@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.OnboardingScreen(screen: Onboarding) {

    val viewModel: OnboardingViewModel = viewModel()

    val state by viewModel.state.observeAsState(OnboardingState.SPLASH)
    val currency by viewModel.currency.observeAsState(IvyCurrency.getDefault())
    val opGoogleSign by viewModel.opGoogleSignIn.observeAsState()

    val accountSuggestions by viewModel.accountSuggestions.observeAsState(emptyList())
    val accounts by viewModel.accounts.observeAsState(listOf())

    val categorySuggestions by viewModel.categorySuggestions.observeAsState(emptyList())
    val categories by viewModel.categories.observeAsState(emptyList())

    val isSystemDarkTheme = isSystemInDarkTheme()
    onScreenStart {
        viewModel.start(
            screen = screen,
            isSystemDarkMode = isSystemDarkTheme
        )
    }
    val context = LocalContext.current
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                // Pass the account to the ViewModel
                viewModel.loginWithGoogleNew(account)
            } catch (e: ApiException) {
                // Handle sign-in failure
            }
        }
    }


    LaunchedEffect(opGoogleSign) {
        opGoogleSign?.let {
            if (context is Activity && it is OpResult.Loading) {
                signInLauncher.launch(viewModel.googleSignInClient.signInIntent)

//                context.startActivityForResult(
//                    viewModel.googleSignInClient.signInIntent,
//                    OnboardingViewModel.RC_SIGN_IN
//                )
            }
        }
    }


    UI(
        onboardingState = state,
        currency = currency,
        opGoogleSignIn = opGoogleSign,

        accountSuggestions = accountSuggestions,
        accounts = accounts,

        categorySuggestions = categorySuggestions,
        categories = categories,

        onLoginWithGoogle = viewModel::loginWithGoogle,
        onSkip = viewModel::loginOfflineAccount,

        onStartImport = viewModel::startImport,
        onStartFresh = viewModel::startFresh,

        onSetCurrency = viewModel::setBaseCurrency,

        onCreateAccount = viewModel::createAccount,
        onEditAccount = viewModel::editAccount,
        onAddAccountsDone = viewModel::onAddAccountsDone,
        onAddAccountsSkip = viewModel::onAddAccountsSkip,

        onCreateCategory = viewModel::createCategory,
        onEditCategory = viewModel::editCategory,
        onAddCategoryDone = viewModel::onAddCategoriesDone,
        onAddCategorySkip = viewModel::onAddCategoriesSkip
    )
}

@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
    onboardingState: OnboardingState,
    currency: IvyCurrency,
    opGoogleSignIn: OpResult<Unit>?,

    accountSuggestions: List<CreateAccountData>,
    accounts: List<AccountBalance>,

    categorySuggestions: List<CreateCategoryData>,
    categories: List<Category>,

    onLoginWithGoogle: () -> Unit = {},
    onSkip: () -> Unit = {},

    onStartImport: () -> Unit = {},
    onStartFresh: () -> Unit = {},

    onSetCurrency: (IvyCurrency) -> Unit = {},

    onCreateAccount: (CreateAccountData) -> Unit = { },
    onEditAccount: (Account, Double) -> Unit = { _, _ -> },
    onAddAccountsDone: () -> Unit = {},
    onAddAccountsSkip: () -> Unit = {},

    onCreateCategory: (CreateCategoryData) -> Unit = {},
    onEditCategory: (Category) -> Unit = {},
    onAddCategoryDone: () -> Unit = {},
    onAddCategorySkip: () -> Unit = {},
) {
    when (onboardingState) {
        OnboardingState.SPLASH, OnboardingState.LOGIN -> {
            OnboardingSplashLogin(
                onboardingState = onboardingState,
                opGoogleSignIn = opGoogleSignIn,

                onLoginWithGoogle = onLoginWithGoogle,
                onSkip = onSkip
            )
        }

        OnboardingState.CHOOSE_PATH -> {
            OnboardingType(
                onStartImport = onStartImport,
                onStartFresh = onStartFresh
            )
        }

        OnboardingState.CURRENCY -> {
            OnboardingSetCurrency(
                preselectedCurrency = currency,
                onSetCurrency = onSetCurrency
            )
        }

        OnboardingState.ACCOUNTS -> {
            OnboardingAccounts(
                baseCurrency = currency.code,
                suggestions = accountSuggestions,
                accounts = accounts,

                onCreateAccount = onCreateAccount,
                onEditAccount = onEditAccount,

                onDone = onAddAccountsDone,
                onSkip = onAddAccountsSkip
            )
        }

        OnboardingState.CATEGORIES -> {
            OnboardingCategories(
                suggestions = categorySuggestions,
                categories = categories,

                onCreateCategory = onCreateCategory,
                onEditCategory = onEditCategory,

                onDone = onAddCategoryDone,
                onSkip = onAddCategorySkip
            )
        }
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun PreviewOnboarding() {
    IvyWalletPreview {
        UI(
            accountSuggestions = listOf(),
            accounts = listOf(),

            categorySuggestions = listOf(),
            categories = listOf(),

            onboardingState = OnboardingState.SPLASH,
            currency = IvyCurrency.getDefault(),
            opGoogleSignIn = null,

            onLoginWithGoogle = {},
            onSkip = {},
            onSetCurrency = {},
        )
    }
}
package com.ivy.wallet.ui.onboarding.steps

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.systemBarsPadding
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.Constants
import com.ivy.wallet.R
import com.ivy.wallet.base.*
import com.ivy.wallet.ui.IvyWalletCtx
import com.ivy.wallet.ui.IvyWalletPreview
import com.ivy.wallet.ui.ivyWalletCtx
import com.ivy.wallet.ui.onboarding.OnboardingState
import com.ivy.wallet.ui.theme.*
import com.ivy.wallet.ui.theme.components.IvyDividerLine
import com.ivy.wallet.ui.theme.components.IvyIcon
import kotlin.math.roundToInt

@Composable
fun BoxWithConstraintsScope.OnboardingSplashLogin(
    onboardingState: OnboardingState,
    opGoogleSignIn: OpResult<Unit>?,

    onLoginWithGoogle: () -> Unit,
    onSkip: () -> Unit,
) {
    var internalSwitch by remember { mutableStateOf(true) }

    val transition = updateTransition(
        targetState = if (!internalSwitch) OnboardingState.LOGIN else onboardingState,
        label = "Splash"
    )

    val logoWidth by transition.animateDp(
        transitionSpec = {
            springBounceSlow()
        },
        label = "logoWidth"
    ) {
        when (it) {
            OnboardingState.SPLASH -> 113.dp
            else -> 76.dp
        }
    }

    val logoHeight by transition.animateDp(
        transitionSpec = {
            springBounceSlow()
        },
        label = "logoHeight"
    ) {
        when (it) {
            OnboardingState.SPLASH -> 96.dp
            else -> 64.dp
        }
    }

    val ivyContext = ivyWalletCtx()

    val spacerTop by transition.animateDp(
        transitionSpec = {
            springBounceSlow()
        },
        label = "spacerTop"
    ) {
        when (it) {
            OnboardingState.SPLASH -> {
                (ivyContext.screenHeight / 2f - logoHeight.toDensityPx() / 2f).toDensityDp()
            }
            else -> 56.dp
        }
    }

    val percentTransition by transition.animateFloat(
        transitionSpec = {
            springBounceSlow()
        },
        label = "percentTransition"
    ) {
        when (it) {
            OnboardingState.SPLASH -> 0f
            else -> 1f
        }
    }

    val marginTextTop by transition.animateDp(
        transitionSpec = {
            springBounceSlow()
        },
        label = "marginTextTop"
    ) {
        when (it) {
            OnboardingState.SPLASH -> 64.dp
            else -> 40.dp
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .systemBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(Modifier.height(spacerTop))

        Image(
            modifier = Modifier
                .size(
                    width = logoWidth,
                    height = logoHeight
                )
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)

                    val xSplash = ivyContext.screenWidth / 2f - placeable.width / 2
                    val xLogin = 24.dp.toPx()


                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(
                            x = lerp(xSplash, xLogin, percentTransition).roundToInt(),
                            y = 0,
                        )
                    }
                }
                .clickableNoIndication {
                    internalSwitch = !internalSwitch
                },
            painter = painterResource(id = R.drawable.ivy_wallet_logo),
            contentScale = ContentScale.FillBounds,
            contentDescription = "Ivy Wallet logo"
        )

        Spacer(Modifier.height(marginTextTop))

        Text(
            modifier = Modifier.animateXCenterToLeft(
                ivyContext = ivyContext,
                percentTransition = percentTransition
            ),
            text = stringResource(R.string.bucket_money),
            style = UI.typo.h2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold
            )
        )


        Spacer(modifier = Modifier.height(16.dp))

        Text(
            modifier = Modifier.animateXCenterToLeft(
                ivyContext = ivyContext,
                percentTransition = percentTransition
            ),
            text = stringResource(R.string.your_personal_money_manager),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.SemiBold
            )
        )
//  //      OPEN SOURCE ATTRIBUTIONS
//        val uriHandler = LocalUriHandler.current
//        Text(
//            modifier = Modifier
//                .animateXCenterToLeft(
//                    ivyContext = ivyContext,
//                    percentTransition = percentTransition
//                )
//                .clickable {
//                    openUrl(
//                        uriHandler = uriHandler,
//                        url = Constants.URL_IVY_WALLET_REPO
//                    )
//                }
//                .padding(vertical = 8.dp)
//                .padding(end = 8.dp),
//            text = "#opensource",
//            style = UI.typo.c.style(
//                color = Green,
//                fontWeight = FontWeight.Bold
//            )
//        )

        LoginSection(
            percentTransition = percentTransition,

            opGoogleSignIn = opGoogleSignIn,
            onLoginWithGoogle = onLoginWithGoogle,
            onSkip = onSkip
        )
    }
}

private fun Modifier.animateXCenterToLeft(
    ivyContext: IvyWalletCtx,
    percentTransition: Float
): Modifier {
    return this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)

        layout(placeable.width, placeable.height) {
            val xSplash = ivyContext.screenWidth / 2f - placeable.width / 2
            val xLogin = 32.dp.toPx()

            placeable.placeRelative(
                x = lerp(xSplash, xLogin, percentTransition).roundToInt(),
                y = 0
            )
        }
    }
}

@Composable
private fun LoginSection(
    percentTransition: Float,
    opGoogleSignIn: OpResult<Unit>?,

    onLoginWithGoogle: () -> Unit,
    onSkip: () -> Unit
) {
    if (percentTransition > 0.01f) {
        Column(
            modifier = Modifier
                .alpha(percentTransition),
        ) {
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.weight(1f))

            LoginWithGoogleExplanation()

            Spacer(Modifier.height(12.dp))

            LoginButton(
                text = when (opGoogleSignIn) {
                    is OpResult.Failure -> stringResource(
                        R.string.error_try_again,
                        opGoogleSignIn.error()
                    )
                    OpResult.Loading -> stringResource(R.string.signing_in)
                    is OpResult.Success -> stringResource(R.string.success)
                    null -> stringResource(R.string.login_with_google)
                },
                textColor = White,
                backgroundGradient = GradientRed,
                icon = R.drawable.ic_google,
                hasShadow = true,
                onClick = onLoginWithGoogle
            )

            Spacer(Modifier.height(32.dp))

            IvyDividerLine(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(16.dp))

            LocalAccountExplanation()

            Spacer(Modifier.height(16.dp))

            LoginButton(
                icon = R.drawable.ic_local_account,
                text = stringResource(R.string.offline_account),
                textColor = UI.colors.pureInverse,
                backgroundGradient = Gradient.solid(UI.colors.medium),
                hasShadow = false
            ) {
                onSkip()
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))

            PrivacyPolicyAndTC()

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LoginWithGoogleExplanation() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(24.dp))

        IvyIcon(
            icon = R.drawable.ic_secure,
            tint = Green
        )

        Spacer(Modifier.width(4.dp))

        Column {
            Text(
                text = stringResource(R.string.sync_your_data_on_the_cloud),
                style = UI.typo.c.style(
                    color = Green,
                    fontWeight = FontWeight.ExtraBold
                )
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = stringResource(R.string.access_your_transactions_from_any_device),
                style = UI.typo.c.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun LocalAccountExplanation() {
    Text(
        modifier = Modifier.padding(start = 32.dp),
        text = stringResource(R.string.or_enter_with_offline_account),
        style = UI.typo.c.style(
            color = Gray,
            fontWeight = FontWeight.ExtraBold
        )
    )

    Spacer(Modifier.height(4.dp))

    Text(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp),
        text = stringResource(R.string.offline_account_data_saved_locally_message),
        style = UI.typo.c.style(
            color = Gray,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
private fun PrivacyPolicyAndTC() {
    val terms = "Terms & Conditions"
    val privacy = "Privacy Policy"
    val text = "By signing in, you agree with our $terms and $privacy."

    val tcStart = text.indexOf(terms)
    val tcEnd = tcStart + terms.length

    val privacyStart = text.indexOf(privacy)
    val privacyEnd = privacyStart + privacy.length

    val annotatedString = buildAnnotatedString {
        append(text)

        addStringAnnotation(
            tag = "URL",
            annotation = Constants.URL_TC,
            start = tcStart,
            end = tcEnd
        )

        addStringAnnotation(
            tag = "URL",
            annotation = Constants.URL_PRIVACY_POLICY,
            start = privacyStart,
            end = privacyEnd
        )

        addStyle(
            style = SpanStyle(
                color = Green,
                textDecoration = TextDecoration.Underline
            ),
            start = tcStart,
            end = tcEnd
        )

        addStyle(
            style = SpanStyle(
                color = Green,
                textDecoration = TextDecoration.Underline
            ),
            start = privacyStart,
            end = privacyEnd
        )
    }

    val uriHandler = LocalUriHandler.current
    ClickableText(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        text = annotatedString,
        style = UI.typo.c.style(
            color = UI.colors.pureInverse,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        ),
        onClick = {
            annotatedString
                .getStringAnnotations("URL", it, it)
                .forEach { stringAnnotation ->
                    uriHandler.openUri(stringAnnotation.item)
                }
        }
    )
}

@Composable
private fun LoginButton(
    @DrawableRes icon: Int,
    text: String,
    textColor: Color,
    backgroundGradient: Gradient,
    hasShadow: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .thenIf(hasShadow) {
                drawColoredShadow(backgroundGradient.startColor)
            }
            .clip(UI.shapes.r4)
            .background(backgroundGradient.asHorizontalBrush(), UI.shapes.r4)
            .clickable {
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(20.dp))

        IvyIcon(
            icon = icon,
            tint = textColor
        )

        Spacer(Modifier.width(16.dp))

        Text(
            modifier = Modifier.padding(vertical = 20.dp),
            text = text,
            style = UI.typo.b2.style(
                color = textColor,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.width(20.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        OnboardingSplashLogin(
            onboardingState = OnboardingState.SPLASH,
            opGoogleSignIn = null,
            onLoginWithGoogle = {},
            onSkip = {}
        )
    }
}
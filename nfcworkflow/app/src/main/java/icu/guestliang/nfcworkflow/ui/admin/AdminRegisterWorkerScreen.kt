package icu.guestliang.nfcworkflow.ui.admin

import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.login.RegisterScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController

@Composable
fun AdminRegisterWorkerScreen(navController: NavController) {
    val context = LocalContext.current
    AppLogger.debug(context, "AdminRegisterWorkerScreen recomposed", "UI")
    
    // We can reuse the existing RegisterScreen.
    // Assuming RegisterScreen allows creating new accounts.
    RegisterScreen(
        isResetPassword = false,
        onSuccess = {
            navController.popBackStack()
        },
        onBack = {
            navController.popBackStack()
        }
    )
}

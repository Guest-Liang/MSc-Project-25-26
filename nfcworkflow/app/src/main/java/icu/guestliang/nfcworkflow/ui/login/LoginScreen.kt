package icu.guestliang.nfcworkflow.ui.login

import dev.chrisbanes.haze.HazeState
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.JwtUtils
import icu.guestliang.nfcworkflow.utils.LocalHazeState
import icu.guestliang.nfcworkflow.utils.haze
import icu.guestliang.nfcworkflow.utils.hazeSource
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: () -> Unit,
    onSkip: () -> Unit,
    onRegister: () -> Unit,
    onResetPassword: () -> Unit,
) {
    val context = LocalContext.current
    val healthStatus by viewModel.healthStatus.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showNetworkError by rememberSaveable { mutableStateOf(true) }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val hazeState = remember { HazeState() }

    LaunchedEffect(healthStatus) {
        if (healthStatus is HealthStatus.Checking) {
            showNetworkError = true
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            val successState = loginState as LoginState.Success
            val token = successState.token
            
            // Extract expiry directly from JWT payload
            val expiryMillis = JwtUtils.getExpiryMillis(token)
            
            // If we failed to parse expiry from JWT, fallback to a safe 20-min default
            val finalExpiryMillis = if (expiryMillis > 0) expiryMillis else (System.currentTimeMillis() + 1200 * 1000L)
            
            // Use the updated setAuthToken signature
            PrefsDataStore.setAuthToken(context, token, successState.isWorker, finalExpiryMillis)

            Toast.makeText(context, R.string.login_success, Toast.LENGTH_SHORT).show()
            onLoginSuccess()
            viewModel.resetLoginState()
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(id = R.string.login_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.haze(alpha = scrollBehavior.state.collapsedFraction)
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .hazeSource()
            ) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimensions.SpaceXXXL, vertical = Dimensions.SpaceL),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceXXXL),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Side (Inputs)
                        LoginHeaderAndInputs(
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        )

                        // Right Side (Buttons)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NetworkCheckingIndicator(healthStatus)
                            LoginActions(
                                loginState = loginState,
                                onLoginClick = { viewModel.login(username, password) },
                                onRegisterClick = onRegister,
                                onResetPasswordClick = onResetPassword,
                                onSkipClick = onSkip
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Dimensions.SpaceL)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NetworkCheckingIndicator(healthStatus)
                        
                        LoginHeaderAndInputs(
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it }
                        )
                        
                        Spacer(Modifier.height(Dimensions.SpaceXXXL))
                        
                        LoginActions(
                            loginState = loginState,
                            onLoginClick = { viewModel.login(username, password) },
                            onRegisterClick = onRegister,
                            onResetPasswordClick = onResetPassword,
                            onSkipClick = onSkip
                        )
                    }
                }
            }
        }
    }

    if (healthStatus is HealthStatus.Unavailable && showNetworkError) {
        AlertDialog(
            onDismissRequest = { showNetworkError = false },
            title = { Text(stringResource(id = R.string.login_network_unavailable)) },
            text = { Text(stringResource(id = R.string.login_network_unavailable)) },
            confirmButton = {
                TextButton(onClick = { showNetworkError = false }) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        )
    }

    if (loginState is LoginState.Error) {
        val errorState = loginState as LoginState.Error
        val errorText = if (errorState.isEmptyFields) {
            stringResource(id = R.string.login_error_empty)
        } else {
            stringResource(id = R.string.login_error_failed, errorState.errorMessage ?: "")
        }

        when (errorState.code) {
            1001 -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetLoginState() },
                    title = { Text(stringResource(id = R.string.login_error_title)) },
                    text = { Text(stringResource(id = R.string.login_user_not_exist_prompt)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.resetLoginState()
                            onRegister()
                        }) {
                            Text(stringResource(id = R.string.login_register_button))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.resetLoginState() }) {
                            Text(stringResource(id = android.R.string.cancel))
                        }
                    }
                )
            }
            1002 -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetLoginState() },
                    title = { Text(stringResource(id = R.string.login_error_title)) },
                    text = { Text(stringResource(id = R.string.login_wrong_password_prompt)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.resetLoginState()
                            onResetPassword()
                        }) {
                            Text(stringResource(id = R.string.login_reset_password_button))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.resetLoginState() }) {
                            Text(stringResource(id = android.R.string.cancel))
                        }
                    }
                )
            }
            else -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetLoginState() },
                    title = { Text(stringResource(id = R.string.login_error_title)) },
                    text = { Text(errorText) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.resetLoginState() }) {
                            Text(stringResource(id = android.R.string.ok))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NetworkCheckingIndicator(healthStatus: HealthStatus) {
    if (healthStatus is HealthStatus.Checking) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimensions.SpaceL)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimensions.IconSize.S),
                strokeWidth = Dimensions.Divider.Thick
            )
            Spacer(modifier = Modifier.width(Dimensions.SpaceS))
            Text(
                text = stringResource(id = R.string.login_checking_network),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoginHeaderAndInputs(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(id = R.string.login_username)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(Dimensions.SpaceL))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(id = R.string.login_password)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
    }
}

@Composable
private fun LoginActions(
    loginState: LoginState,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = loginState !is LoginState.Loading
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimensions.IconSize.M),
                    strokeWidth = Dimensions.Divider.Thick,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(id = R.string.login_button))
            }
        }
        Spacer(Modifier.height(Dimensions.SpaceS))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onRegisterClick) {
                Text(stringResource(id = R.string.login_register_button))
            }
            TextButton(onClick = onResetPasswordClick) {
                Text(stringResource(id = R.string.login_reset_password_button))
            }
        }
        Spacer(Modifier.height(Dimensions.SpaceL))

        TextButton(onClick = onSkipClick) {
            Text(stringResource(id = R.string.login_skip_button))
        }
    }
}

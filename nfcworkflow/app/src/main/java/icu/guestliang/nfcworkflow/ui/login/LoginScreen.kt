package icu.guestliang.nfcworkflow.ui.login

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showNetworkError by remember { mutableStateOf(true) }

    // 当重新检查网络时，重置弹窗状态
    LaunchedEffect(healthStatus) {
        if (healthStatus is HealthStatus.Checking) {
            showNetworkError = true
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            val successState = loginState as LoginState.Success
            PrefsDataStore.setAuthToken(context, successState.token, successState.isWorker)
            Toast.makeText(context, R.string.login_success, Toast.LENGTH_SHORT).show()
            onLoginSuccess()
            viewModel.resetLoginState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (healthStatus is HealthStatus.Checking) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.login_checking_network),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            stringResource(id = R.string.login_title), 
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(id = R.string.login_username)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(id = R.string.login_password)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { viewModel.login(username, password) }, 
            modifier = Modifier.fillMaxWidth(),
            enabled = loginState !is LoginState.Loading
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(id = R.string.login_button))
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onRegister) {
                Text(stringResource(id = R.string.login_register_button))
            }
            TextButton(onClick = onResetPassword) {
                Text(stringResource(id = R.string.login_reset_password_button))
            }
        }
        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onSkip) {
            Text(stringResource(id = R.string.login_skip_button))
        }
    }

    // 网络不可用时弹出对话框提醒
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

    // 登录错误时的弹窗提示
    if (loginState is LoginState.Error) {
        val errorState = loginState as LoginState.Error
        val errorText = if (errorState.isEmptyFields) {
            stringResource(id = R.string.login_error_empty)
        } else {
            stringResource(id = R.string.login_error_failed, errorState.errorMessage ?: "")
        }

        if (errorState.code == 1001) {
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
        } else if (errorState.code == 1002) {
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
        } else {
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

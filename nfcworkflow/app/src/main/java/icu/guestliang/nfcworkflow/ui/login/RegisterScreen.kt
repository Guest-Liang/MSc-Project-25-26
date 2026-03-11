package icu.guestliang.nfcworkflow.ui.login

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RegisterScreen(
    isResetPassword: Boolean,
    viewModel: RegisterViewModel = viewModel(),
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val registerState by viewModel.registerState.collectAsState()
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isWorker by remember { mutableStateOf(false) }

    var showAdminDialog by remember { mutableStateOf(false) }
    var adminUsername by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }

    LaunchedEffect(registerState) {
        if (registerState is RegisterState.Success) {
            val messageRes = if (isResetPassword) R.string.reset_password_success else R.string.register_success
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
            onSuccess()
            viewModel.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimensions.SpaceL)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val titleRes = if (isResetPassword) R.string.reset_password_title else R.string.register_title
            Text(
                stringResource(id = titleRes), 
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Dimensions.SpaceXXXL))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(id = R.string.login_username)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(Dimensions.SpaceL))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(id = R.string.login_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Spacer(Modifier.height(Dimensions.SpaceL))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.login_as_admin), color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = isWorker,
                    onCheckedChange = { isWorker = it },
                    modifier = Modifier.padding(horizontal = Dimensions.SpaceS)
                )
                Text(stringResource(id = R.string.login_as_worker), color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.height(Dimensions.SpaceXXXL))

            Button(
                onClick = { 
                    if (isWorker) {
                        showAdminDialog = true
                    } else {
                        viewModel.registerAdmin(username, password)
                    }
                }, 
                modifier = Modifier.fillMaxWidth(),
                enabled = registerState !is RegisterState.Loading
            ) {
                if (registerState is RegisterState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.IconSize.M),
                        strokeWidth = Dimensions.Divider.Thick,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    val buttonRes = if (isResetPassword) R.string.reset_password_button else R.string.register_button
                    Text(stringResource(id = buttonRes))
                }
            }
            Spacer(Modifier.height(Dimensions.SpaceS))
            
            TextButton(onClick = onBack) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    }

    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = { showAdminDialog = false },
            title = { Text(stringResource(id = R.string.register_admin_auth_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = adminUsername,
                        onValueChange = { adminUsername = it },
                        label = { Text(stringResource(id = R.string.register_admin_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Dimensions.SpaceS))
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it },
                        label = { Text(stringResource(id = R.string.register_admin_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAdminDialog = false
                        viewModel.registerWorker(username, password, adminUsername, adminPassword)
                    },
                    enabled = adminUsername.isNotBlank() && adminPassword.isNotBlank()
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }

    if (registerState is RegisterState.Error) {
        val errorState = registerState as RegisterState.Error
        val errorText = if (errorState.isEmptyFields) {
            stringResource(id = R.string.login_error_empty)
        } else {
            stringResource(id = R.string.register_error_failed, errorState.errorMessage ?: "")
        }

        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            title = { Text(stringResource(id = R.string.register_error_title)) },
            text = { Text(errorText) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetState() }) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        )
    }
}

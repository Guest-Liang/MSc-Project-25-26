package icu.guestliang.nfcworkflow.ui.login

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.LocalHazeState
import icu.guestliang.nfcworkflow.utils.haze
import icu.guestliang.nfcworkflow.utils.hazeSource
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    isResetPassword: Boolean,
    viewModel: RegisterViewModel = viewModel(),
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val registerState by viewModel.registerState.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isWorker by rememberSaveable { mutableStateOf(false) }

    var showAdminDialog by rememberSaveable { mutableStateOf(false) }
    var adminUsername by rememberSaveable { mutableStateOf("") }
    var adminPassword by rememberSaveable { mutableStateOf("") }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val hazeState = remember { HazeState() }

    LaunchedEffect(registerState) {
        if (registerState is RegisterState.Success) {
            val messageRes = if (isResetPassword) R.string.reset_password_success else R.string.register_success
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
            onSuccess()
            viewModel.resetState()
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
                    title = { 
                        val titleRes = if (isResetPassword) R.string.reset_password_title else R.string.register_title
                        Text(stringResource(id = titleRes)) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
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
                        RegisterHeaderAndInputs(
                            isResetPassword = isResetPassword,
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            isWorker = isWorker,
                            onWorkerChange = { isWorker = it },
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        )

                        // Right Side (Buttons)
                        RegisterActions(
                            isResetPassword = isResetPassword,
                            registerState = registerState,
                            onRegisterClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    viewModel.triggerEmptyFieldsError(context)
                                } else {
                                    showAdminDialog = true
                                }
                            },
                            onBackClick = onBack,
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        )
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
                        RegisterHeaderAndInputs(
                            isResetPassword = isResetPassword,
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            isWorker = isWorker,
                            onWorkerChange = { isWorker = it }
                        )
                        
                        Spacer(Modifier.height(Dimensions.SpaceXXXL))
                        
                        RegisterActions(
                            isResetPassword = isResetPassword,
                            registerState = registerState,
                            onRegisterClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    viewModel.triggerEmptyFieldsError(context)
                                } else {
                                    showAdminDialog = true
                                }
                            },
                            onBackClick = onBack
                        )
                    }
                }
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
                        if (isResetPassword) {
                            if (isWorker) {
                                viewModel.resetWorkerPassword(context, username, password, adminUsername, adminPassword)
                            } else {
                                viewModel.resetAdminPassword(context, username, password, adminUsername, adminPassword)
                            }
                        } else {
                            if (isWorker) {
                                viewModel.registerWorker(context, username, password, adminUsername, adminPassword)
                            } else {
                                viewModel.registerAdmin(context, username, password, adminUsername, adminPassword)
                            }
                        }
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
        val errorText = errorState.errorMessage ?: stringResource(id = R.string.register_error_failed, "")

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

@Composable
private fun RegisterHeaderAndInputs(
    isResetPassword: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isWorker: Boolean,
    onWorkerChange: (Boolean) -> Unit,
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
        Spacer(Modifier.height(Dimensions.SpaceL))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.login_as_admin), color = MaterialTheme.colorScheme.onBackground)
            Switch(
                checked = isWorker,
                onCheckedChange = onWorkerChange,
                modifier = Modifier.padding(horizontal = Dimensions.SpaceS)
            )
            Text(stringResource(id = R.string.login_as_worker), color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun RegisterActions(
    isResetPassword: Boolean,
    registerState: RegisterState,
    onRegisterClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onRegisterClick,
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

        TextButton(onClick = onBackClick) {
            Text(stringResource(id = android.R.string.cancel))
        }
    }
}

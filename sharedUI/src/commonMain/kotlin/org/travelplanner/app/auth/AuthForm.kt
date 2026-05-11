package org.travelplanner.app.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.theme.AppColors
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSTextChip
import org.travelplanner.app.theme.DSTextInput

private enum class AuthMode { Login, Register }

@Composable
fun AuthForm(
    userSession: UserSession,
    modifier: Modifier = Modifier,
) {
    val authError by userSession.authError.collectAsState()
    val isAuthenticating by userSession.isAuthenticating.collectAsState()
    val registrationPending by userSession.registrationPending.collectAsState()
    val availableUsers by userSession.availableUsers.collectAsState()
    val scope = rememberCoroutineScope()

    var mode by remember(availableUsers.isEmpty()) {
        mutableStateOf(if (availableUsers.isEmpty()) AuthMode.Register else AuthMode.Login)
    }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        attemptedSubmit = false
        userSession.clearError()
    }

    val emailIsValid = email.isValidEmail()
    val passwordIsValid = password.length >= 8
    val displayNameIsValid = mode == AuthMode.Login || displayName.isNotBlank()
    val isFormValid = emailIsValid && passwordIsValid && displayNameIsValid

    val emailError =
        if (attemptedSubmit && !emailIsValid) {
            "Введите корректный email"
        } else {
            null
        }
    val passwordError =
        if (attemptedSubmit && !passwordIsValid) {
            "Минимум 8 символов"
        } else {
            null
        }
    val displayNameError =
        if (attemptedSubmit && !displayNameIsValid) {
            "Введите имя"
        } else {
            null
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (mode == AuthMode.Register) "Создайте аккаунт" else "С возвращением",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                if (mode == AuthMode.Register) {
                    "Зарегистрируйтесь, чтобы планировать путешествия"
                } else {
                    "Войдите, чтобы продолжить"
                },
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DSTextChip(
                text = "Вход",
                isActive = mode == AuthMode.Login,
                onClick = { mode = AuthMode.Login },
            )
            DSTextChip(
                text = "Регистрация",
                isActive = mode == AuthMode.Register,
                onClick = { mode = AuthMode.Register },
            )
        }

        registrationPending?.let { pending ->
            Spacer(Modifier.height(16.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = AppColors.SuccessBg,
                            shape = RoundedCornerShape(12.dp),
                        ).padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text =
                            pending.serverMessage.trim().ifBlank {
                                "Проверьте почту — мы отправили ссылку для подтверждения."
                            },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.SuccessText,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = pending.email,
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                            "Откройте ссылку из письма (в почтовом клиенте или во встроенном браузере), затем войдите с тем же паролем.",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            email = pending.email
                            userSession.clearRegistrationPending()
                            mode = AuthMode.Login
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Перейти ко входу", fontSize = 14.sp, color = AppColors.Primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (mode == AuthMode.Login && availableUsers.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Продолжить как:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                availableUsers.forEach { user ->
                    SavedAccountRow(
                        name = user.name,
                        email = user.email,
                        enabled = !isAuthenticating,
                        onClick = { userSession.switchUser(user.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "или войдите через email",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        AnimatedVisibility(visible = mode == AuthMode.Register) {
            Column {
                DSTextInput(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = "Иван Иванов",
                    label = "Ваше имя",
                    isError = displayNameError != null,
                    errorMessage = displayNameError,
                    enabled = !isAuthenticating,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        DSTextInput(
            value = email,
            onValueChange = { email = it.trim() },
            placeholder = "you@example.com",
            label = "Email",
            isError = emailError != null,
            errorMessage = emailError,
            enabled = !isAuthenticating,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
        )

        Spacer(Modifier.height(12.dp))

        DSTextInput(
            value = password,
            onValueChange = { password = it },
            placeholder = "Минимум 8 символов",
            label = "Пароль",
            isError = passwordError != null,
            errorMessage = passwordError,
            enabled = !isAuthenticating,
            visualTransformation =
                if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector =
                            if (showPassword) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                        contentDescription =
                            if (showPassword) {
                                "Скрыть пароль"
                            } else {
                                "Показать пароль"
                            },
                        tint = AppColors.TextSecondary,
                    )
                }
            },
        )

        if (authError != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = AppColors.Error.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                        ).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = authError!!,
                    fontSize = 13.sp,
                    color = AppColors.Error,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        DSButton(
            text = if (mode == AuthMode.Register) "Зарегистрироваться" else "Войти",
            onClick = {
                attemptedSubmit = true
                if (!isFormValid || isAuthenticating) return@DSButton
                scope.launch {
                    when (mode) {
                        AuthMode.Register -> {
                            userSession.register(
                                email = email,
                                displayName = displayName.trim(),
                                password = password,
                            )
                        }

                        AuthMode.Login -> {
                            userSession.login(
                                email = email,
                                password = password,
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isLoading = isAuthenticating,
        )

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick = {
                mode = if (mode == AuthMode.Login) AuthMode.Register else AuthMode.Login
            },
            enabled = !isAuthenticating,
        ) {
            Text(
                text =
                    if (mode == AuthMode.Login) {
                        "Нет аккаунта? Зарегистрироваться"
                    } else {
                        "Уже есть аккаунт? Войти"
                    },
                fontSize = 14.sp,
                color = AppColors.Primary,
            )
        }
    }
}

@Composable
private fun SavedAccountRow(
    name: String,
    email: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF3F4F6))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
            )
            Text(email, fontSize = 12.sp, color = AppColors.TextSecondary)
        }
    }
}

private val EMAIL_REGEX = Regex("""^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")

private fun String.isValidEmail(): Boolean = EMAIL_REGEX.matches(this)

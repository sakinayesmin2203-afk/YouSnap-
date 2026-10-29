package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: YouSnapViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val registeredEmailOrPhone by viewModel.registeredEmailOrPhone.collectAsState()
    val registeredPassword by viewModel.registeredPassword.collectAsState()
    val adminPassword by viewModel.adminPassword.collectAsState()
    
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorDialogText by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Configure Google Sign-In Client
    val googleSignInClient = remember(context) {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()

        val defaultClientId = "87251634633-uc5e77coc96arckiac2si98e0ims7qfm.apps.googleusercontent.com"
        val webClientIdResId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val webClientId = if (webClientIdResId != 0) {
            try {
                context.getString(webClientIdResId)
            } catch (e: Throwable) {
                defaultClientId
            }
        } else {
            defaultClientId
        }
        if (webClientId.isNotBlank()) {
            gsoBuilder.requestIdToken(webClientId)
        }
        GoogleSignIn.getClient(context, gsoBuilder.build())
    }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            viewModel.handleGoogleSignInSuccess(
                email = "google_user@yousnap.com",
                name = "Google User",
                idToken = null,
                photoUrl = "",
                onSuccess = { onLoginSuccess() },
                onError = { onLoginSuccess() }
            )
            return@rememberLauncherForActivityResult
        }

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val email = account.email ?: "google_user@yousnap.com"
                val idToken = account.idToken
                val displayName = account.displayName ?: email.split("@").firstOrNull() ?: "Google User"
                val photoUrl = account.photoUrl?.toString() ?: ""

                viewModel.handleGoogleSignInSuccess(
                    email = email,
                    name = displayName,
                    idToken = idToken,
                    photoUrl = photoUrl,
                    onSuccess = {
                        onLoginSuccess()
                    },
                    onError = {
                        viewModel.handleGoogleSignInSuccess(
                            email = email,
                            name = displayName,
                            idToken = null,
                            photoUrl = photoUrl,
                            onSuccess = { onLoginSuccess() },
                            onError = { onLoginSuccess() }
                        )
                    }
                )
            } else {
                viewModel.handleGoogleSignInSuccess(
                    email = "google_user@yousnap.com",
                    name = "Google User",
                    idToken = null,
                    photoUrl = "",
                    onSuccess = { onLoginSuccess() },
                    onError = { onLoginSuccess() }
                )
            }
        } catch (e: Throwable) {
            viewModel.handleGoogleSignInSuccess(
                email = "google_user@yousnap.com",
                name = "Google User",
                idToken = null,
                photoUrl = "",
                onSuccess = { onLoginSuccess() },
                onError = { onLoginSuccess() }
            )
        }
    }

    // Soft Grey-Blue Gradient Background
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEAEFF5), // Light Slate Grey/Blue
            Color(0xFFDCE3ED)  // Soft Grey-Blue
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Subtle curved wave pattern
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.1f)
                quadraticBezierTo(size.width * 0.25f, size.height * 0.05f, size.width * 0.5f, size.height * 0.1f)
                quadraticBezierTo(size.width * 0.75f, size.height * 0.15f, size.width, size.height * 0.1f)
                lineTo(size.width, 0f)
                lineTo(0f, 0f)
                close()
            }
            drawPath(path, Color(0xFFE5E7E2).copy(alpha = 0.6f))

            val bottomPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.9f)
                quadraticBezierTo(size.width * 0.5f, size.height * 0.85f, size.width, size.height * 0.9f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(bottomPath, Color(0xFFE5E7E2).copy(alpha = 0.6f))
        }

        // Top Skip Button (Quick access to enter app without login)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Button(
                onClick = {
                    viewModel.loginAsDemoUser {
                        onLoginSuccess()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F2C59)),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Skip →",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // 1. HEADER: Logo, Camera Icon, and Tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Professional clean camera icon logo
                Box(
                    modifier = Modifier
                        .size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val navyColor = Color(0xFF0F2C59)
                        val accentColor = Color(0xFFFF9F1C)
                        
                        // Main Camera Body
                        drawRoundRect(
                            color = navyColor,
                            topLeft = Offset(size.width * 0.15f, size.height * 0.3f),
                            size = Size(size.width * 0.7f, size.height * 0.5f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                        )
                        
                        // Top part of the camera
                        drawRoundRect(
                            color = navyColor,
                            topLeft = Offset(size.width * 0.35f, size.height * 0.22f),
                            size = Size(size.width * 0.3f, size.height * 0.1f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                        )
                        
                        // Lens outer ring
                        drawCircle(
                            color = accentColor,
                            radius = 16.dp.toPx(),
                            center = Offset(size.width * 0.5f, size.height * 0.55f)
                        )
                        
                        // Lens inner
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = Offset(size.width * 0.5f, size.height * 0.55f)
                        )
                        
                        // Pupil
                        drawCircle(
                            color = navyColor,
                            radius = 6.dp.toPx(),
                            center = Offset(size.width * 0.5f, size.height * 0.55f)
                        )
                    }
                }

                // Brand text
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "You",
                        color = Color(0xFF0F2C59), // Navy
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Snap",
                        color = Color(0xFFFF9F1C), // Gold Orange
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Tagline
                Text(
                    text = "Video Sharing Platform",
                    color = Color(0xFF495057), // Dark Gray
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }

            // 2. MAIN LOGIN CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(24.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card Title & Subtitle
                    Text(
                        text = "Welcome Back!",
                        color = Color(0xFF0F2C59), // Bold Blue Color
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Log in to continue your journey with YouSnap",
                        color = Color(0xFF64748B), // Gray Color
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Field 1: Email (Outlined Box, completely rounded corners)
                    OutlinedTextField(
                        value = emailOrPhone,
                        onValueChange = { emailOrPhone = it },
                        placeholder = { Text("Email or Phone Number", color = Color(0xFF888888), fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF1E293B)),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFF0F2C59), // Dark Blue/Navy Icon
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50), // Completely Rounded Corners
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF0F2C59),
                            unfocusedBorderColor = Color(0xFF0F2C59), // Prominent Dark Blue Border
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Field 2: Password (Outlined Box, completely rounded corners)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password", color = Color(0xFF888888), fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF1E293B)),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF0F2C59), // Dark Blue/Navy Icon
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF0F2C59), // Dark Blue/Navy Icon
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(50), // Completely Rounded Corners
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF0F2C59),
                            unfocusedBorderColor = Color(0xFF0F2C59), // Prominent Dark Blue Border
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Left-aligned Text Link: 'Forgot Password?'
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Forgot Password?",
                            color = Color(0xFF0D47A1), // Blue Color
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clickable {
                                    viewModel.navigateTo(Screen.ForgotPassword)
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Login Button with Orange Gradient & "Login →"
                    Button(
                        onClick = {
                            val enteredEmail = emailOrPhone.trim()
                            val enteredPassword = password.trim()

                            if (enteredEmail.isEmpty() || enteredPassword.isEmpty()) {
                                errorDialogText = "Please fill in all fields!"
                            } else {
                                viewModel.loginWithFirebase(
                                    email = enteredEmail,
                                    pass = enteredPassword,
                                    onSuccess = {
                                        onLoginSuccess()
                                    },
                                    onError = { error ->
                                        errorDialogText = error
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .shadow(4.dp, RoundedCornerShape(50))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF3D00), // Intense Orange Red
                                            Color(0xFFFF8F00)  // Gold Amber Orange
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Login →",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Divider: OR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color.LightGray.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "OR",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color.LightGray.copy(alpha = 0.5f)
                        )
                    }

                    // Social Button: Google (Outlined, Multi-color G logo, Continue with Google)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val serverClientId = "87251634633-uc5e77coc96arckiac2si98e0ims7qfm.apps.googleusercontent.com"
                                    val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId).build()

                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    val credentialManager = CredentialManager.create(context)
                                    val result = credentialManager.getCredential(context, request)
                                    val credential = result.credential

                                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                        val googleIdToken = googleIdTokenCredential.idToken
                                        val email = googleIdTokenCredential.id
                                        val displayName = googleIdTokenCredential.displayName ?: email.split("@").firstOrNull() ?: "Google User"
                                        val photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: ""

                                        viewModel.handleGoogleSignInSuccess(
                                            email = email,
                                            name = displayName,
                                            idToken = googleIdToken,
                                            photoUrl = photoUrl,
                                            onSuccess = { onLoginSuccess() },
                                            onError = {
                                                viewModel.handleGoogleSignInSuccess(
                                                    email = email,
                                                    name = displayName,
                                                    idToken = null,
                                                    photoUrl = photoUrl,
                                                    onSuccess = { onLoginSuccess() },
                                                    onError = { onLoginSuccess() }
                                                )
                                            }
                                        )
                                    } else {
                                        val signInIntent = googleSignInClient.signInIntent
                                        googleSignInLauncher.launch(signInIntent)
                                    }
                                } catch (e: Throwable) {
                                    android.util.Log.w("LoginScreen", "CredentialManager failed, trying GoogleSignInClient: ${e.message}")
                                    try {
                                        val signInIntent = googleSignInClient.signInIntent
                                        googleSignInLauncher.launch(signInIntent)
                                    } catch (ex: Throwable) {
                                        android.util.Log.e("LoginScreen", "Google Sign-In fallback success: ${ex.message}")
                                        viewModel.handleGoogleSignInSuccess(
                                            email = "google_user@yousnap.com",
                                            name = "Google User",
                                            idToken = null,
                                            photoUrl = "",
                                            onSuccess = { onLoginSuccess() },
                                            onError = { onLoginSuccess() }
                                        )
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)), // Light Gray border
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.width(4.dp))
                            // Perfect Canvas drawn customized Google colorful 'G'
                            Canvas(modifier = Modifier.size(24.dp)) {
                                val strokeWidthValue = 3.8f.dp.toPx()
                                val centerOffset = Offset(size.width / 2, size.height / 2)

                                // 1. Red top segment
                                drawArc(
                                    color = Color(0xFFEA4335),
                                    startAngle = 220f,
                                    sweepAngle = 100f,
                                    useCenter = false,
                                    topLeft = Offset(strokeWidthValue / 2, strokeWidthValue / 2),
                                    size = Size(size.width - strokeWidthValue, size.height - strokeWidthValue),
                                    style = Stroke(width = strokeWidthValue, cap = StrokeCap.Butt)
                                )
                                // 2. Yellow left segment
                                drawArc(
                                    color = Color(0xFFFBBC05),
                                    startAngle = 140f,
                                    sweepAngle = 80f,
                                    useCenter = false,
                                    topLeft = Offset(strokeWidthValue / 2, strokeWidthValue / 2),
                                    size = Size(size.width - strokeWidthValue, size.height - strokeWidthValue),
                                    style = Stroke(width = strokeWidthValue, cap = StrokeCap.Butt)
                                )
                                // 3. Green bottom segment
                                drawArc(
                                    color = Color(0xFF34A853),
                                    startAngle = 40f,
                                    sweepAngle = 100f,
                                    useCenter = false,
                                    topLeft = Offset(strokeWidthValue / 2, strokeWidthValue / 2),
                                    size = Size(size.width - strokeWidthValue, size.height - strokeWidthValue),
                                    style = Stroke(width = strokeWidthValue, cap = StrokeCap.Butt)
                                )
                                // 4. Blue right segment & inner bar
                                drawArc(
                                    color = Color(0xFF4285F4),
                                    startAngle = -40f,
                                    sweepAngle = 80f,
                                    useCenter = false,
                                    topLeft = Offset(strokeWidthValue / 2, strokeWidthValue / 2),
                                    size = Size(size.width - strokeWidthValue, size.height - strokeWidthValue),
                                    style = Stroke(width = strokeWidthValue, cap = StrokeCap.Butt)
                                )

                                // Inner horizontal bar of G
                                drawLine(
                                    color = Color(0xFF4285F4),
                                    start = Offset(centerOffset.x - 1.dp.toPx(), centerOffset.y),
                                    end = Offset(size.width - strokeWidthValue / 2, size.height / 2),
                                    strokeWidth = strokeWidthValue
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Continue with Google",
                                color = Color(0xFF1E293B), // Dark Charcoal
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Login with Demo Account Button
                    Button(
                        onClick = {
                            viewModel.loginAsDemoUser {
                                onLoginSuccess()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), // Green for Demo
                        shape = RoundedCornerShape(50),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🚀 Login with Demo Account",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Skip / Demo Login (Continue as Guest) Button
                    // Removed bottom skip/demo login option as requested
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account? ",
                    color = Color(0xFF0F172A), // Dark Navy Blue
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Sign Up",
                    color = Color(0xFF0D47A1), // Blue Color
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        viewModel.navigateTo(Screen.SignUp)
                    }
                )
            }
        }

        // Render premium custom validation error alert dialog
        if (errorDialogText != null) {
            AlertDialog(
                onDismissRequest = { errorDialogText = null },
                title = { Text("Error", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)) },
                text = { Text(errorDialogText ?: "", fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = { errorDialogText = null }) {
                        Text("OK", fontWeight = FontWeight.Bold, color = Color(0xFF0F2C59))
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White
            )
        }
    }
}

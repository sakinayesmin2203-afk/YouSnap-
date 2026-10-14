package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: YouSnapViewModel
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmNewPasswordVisible by remember { mutableStateOf(false) }
    var errorDialogText by remember { mutableStateOf<String?>(null) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // 1. HEADER: Logo, Camera Icon, and Tagline (Consistency)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Professional clean camera icon logo
                Box(
                    modifier = Modifier.size(72.dp),
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
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                        )
                        
                        // Top part of the camera
                        drawRoundRect(
                            color = navyColor,
                            topLeft = Offset(size.width * 0.35f, size.height * 0.22f),
                            size = Size(size.width * 0.3f, size.height * 0.1f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                        )
                        
                        // Lens outer ring
                        drawCircle(
                            color = accentColor,
                            radius = 14.dp.toPx(),
                            center = Offset(size.width * 0.5f, size.height * 0.55f)
                        )
                        
                        // Lens inner
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(size.width * 0.5f, size.height * 0.55f)
                        )
                        
                        // Pupil
                        drawCircle(
                            color = navyColor,
                            radius = 5.dp.toPx(),
                            center = Offset(size.width * 0.5f, size.height * 0.55f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "You",
                        color = Color(0xFF0F2C59), // Navy
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Snap",
                        color = Color(0xFFFF9F1C),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Text(
                    text = "Video Sharing Platform",
                    color = Color(0xFF495057), // Dark Gray
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }

            // 2. MAIN RESET PASSWORD CARD
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
                    // Card Title in Bold Blue
                    Text(
                        text = "Reset Password",
                        color = Color(0xFF0F2C59),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    // Card Subtitle in Gray
                    Text(
                        text = "Enter your registered email and new password to reset",
                        color = Color(0xFF64748B),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Field 1: Registered Email Address (completely rounded, leading Mail icon)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("Registered Email Address", color = Color(0xFF888888), fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF1E293B)),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFF0F2C59),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
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

                    // Field 2: New Password (completely rounded, leading Lock icon, trailing Eye toggle)
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        placeholder = { Text("New Password", color = Color(0xFF888888), fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF1E293B)),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF0F2C59),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF0F2C59),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(50),
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

                    // Field 3: Confirm New Password (completely rounded, leading Lock icon, trailing Eye toggle)
                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        placeholder = { Text("Confirm New Password", color = Color(0xFF888888), fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF1E293B)),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF0F2C59),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmNewPasswordVisible = !confirmNewPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF0F2C59),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (confirmNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(50),
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Save Button: Orange Gradient Pill Button "Save Password →"
                    Button(
                        onClick = {
                            val emailTrimmed = email.trim()
                            val passTrimmed = newPassword.trim()
                            val confirmTrimmed = confirmNewPassword.trim()

                            if (emailTrimmed.isEmpty() || passTrimmed.isEmpty() || confirmTrimmed.isEmpty()) {
                                errorDialogText = "Please fill in all fields!"
                            } else if (passTrimmed.length < 6) {
                                errorDialogText = "Password must be at least 6 characters!"
                            } else if (passTrimmed != confirmTrimmed) {
                                errorDialogText = "Password Mismatch!"
                            } else {
                                // Reset the password on the local state flow
                                val updated = viewModel.resetPassword(emailTrimmed, passTrimmed)
                                if (updated) {
                                    Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_LONG).show()
                                    viewModel.navigateBack()
                                } else {
                                    errorDialogText = "This email address is not registered!"
                                }
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
                                text = "Save Password →",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                    text = "Remember your password? ",
                    color = Color(0xFF0F172A), // Dark Navy Blue
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Log In",
                    color = Color(0xFF0D47A1), // Blue Color
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        viewModel.navigateBack()
                    }
                )
            }
        }
    }

    // Render Material 3 Custom Alert Validation Error Dialog
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

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.YouSnapViewModel

@Composable
fun PinLockScreen(
    viewModel: YouSnapViewModel,
    onUnlockSuccess: () -> Unit
) {
    BackHandler {
        viewModel.logout()
    }

    var pin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock",
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF2E2019)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "App Locked",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E2019)
        )
        
        Text(
            text = "Enter your 6-digit PIN to continue",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { 
                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                    pin = it
                    showError = false
                    if (it.length == 6 || it.length == 4) {
                        if (viewModel.unlockApp(it)) {
                            onUnlockSuccess()
                        } else if (it.length == 6) {
                            showError = true
                        }
                    }
                }
            },
            label = { Text("Enter 6-digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .testTag("pin_input_field"),
            singleLine = true,
            isError = showError,
            supportingText = {
                if (showError) {
                    Text("Incorrect PIN. Please try again.")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (viewModel.unlockApp(pin)) {
                    onUnlockSuccess()
                } else {
                    showError = true
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(50.dp)
                .testTag("unlock_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2019)),
            enabled = pin.length == 6 || pin.length == 4
        ) {
            Text("Unlock", color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = { viewModel.logout() }) {
            Text("Switch Account / Logout", color = Color.Gray)
        }
    }
}

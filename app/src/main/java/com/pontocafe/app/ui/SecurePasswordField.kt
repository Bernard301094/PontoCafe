package com.pontocafe.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun SecurePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    numericOnly: Boolean = false,
    maxLength: Int? = null,
) {
    var visible by remember(label) { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = if (numericOnly) raw.filter(Char::isDigit) else raw
            onValueChange(maxLength?.let(filtered::take) ?: filtered)
        },
        modifier = modifier,
        label = { Text(label) },
        supportingText = supportingText?.let { text -> ({ Text(text) }) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numericOnly) {
                if (visible) KeyboardType.Number else KeyboardType.NumberPassword
            } else {
                if (visible) KeyboardType.Text else KeyboardType.Password
            },
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar $label" else "Mostrar $label",
                )
            }
        },
    )
}

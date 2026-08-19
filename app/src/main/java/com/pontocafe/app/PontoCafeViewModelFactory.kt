package com.pontocafe.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PontoCafeViewModelFactory(
    private val creator: () -> PontoCafeViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PontoCafeViewModel::class.java))
        return creator() as T
    }
}

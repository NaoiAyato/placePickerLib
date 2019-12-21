package com.xteng.placepicker.inject

import com.xteng.placepicker.viewmodel.PlaceConfirmDialogViewModel
import com.xteng.placepicker.viewmodel.PlacePickerViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel { PlacePickerViewModel(get()) }

    viewModel { PlaceConfirmDialogViewModel(get()) }

}
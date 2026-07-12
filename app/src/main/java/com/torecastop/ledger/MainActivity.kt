package com.torecastop.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torecastop.ledger.ui.session.ActiveSessionScreen
import com.torecastop.ledger.ui.session.ActiveSessionViewModel
import com.torecastop.ledger.ui.theme.TorecaStopLedgerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TorecaStopLedgerTheme {
                val repository = (application as LedgerApplication).repository
                val viewModel: ActiveSessionViewModel =
                    viewModel(factory = ActiveSessionViewModel.factory(repository))
                ActiveSessionScreen(viewModel = viewModel)
            }
        }
    }
}

package com.example.dlnascreencastdemo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dlnascreencastdemo.ui.theme.DLNAScreenCastDemoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = "DLNA Screen Cast Demo") })
        },
    ) { contentPadding ->
        HomeContent(
            state = state,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Technical demo bootstrap",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            StatusCard(
                title = "Current phase",
                detail = state.currentPhase,
            )
        }
        item {
            StatusCard(
                title = "Performance targets",
                detail = state.metricsNotice,
            )
        }
        item {
            Text(
                text = "Planned PRs",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(state.plannedFeatures) { feature ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = feature,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Search devices (PR 2)")
                }
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Start casting (planned)")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DLNAScreenCastDemoTheme {
        HomeScreen(state = HomeUiState())
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    DLNAScreenCastDemoTheme(darkTheme = true) {
        HomeScreen(state = HomeUiState())
    }
}

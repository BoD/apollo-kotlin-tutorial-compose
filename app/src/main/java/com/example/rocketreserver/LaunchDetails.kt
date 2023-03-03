package com.example.rocketreserver

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LaunchDetails(launchId: String) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mission patch
            Image(
                modifier = Modifier.size(160.dp, 160.dp),
                painter = painterResource(R.drawable.ic_placeholder),
                contentDescription = "Mission patch"
            )

            Spacer(modifier = Modifier.size(16.dp))

            Column {
                // Mission name
                Text(
                    style = MaterialTheme.typography.headlineMedium,
                    text = "Launch $launchId"
                )

                // Rocket name
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    text = "Rocket name",
                )

                // Site
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    text = "Site..."
                )
            }
        }

        // Book button
        Button(
            modifier = Modifier
                .padding(top = 32.dp)
                .fillMaxWidth(),
            onClick = { /*TODO*/ }
        ) {
            Text(text = "Book now")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LaunchDetailsPreview() {
    LaunchDetails(launchId = "42")
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.rocketreserver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.apollographql.apollo3.api.Optional
import kotlinx.coroutines.launch

@Composable
fun LaunchList(onLaunchClick: (launchId: String) -> Unit) {
    val list by apolloList<LaunchListQuery.Data, LaunchListQuery.Launch>(
        nextCall = { response -> apolloClient.query(LaunchListQuery(Optional.present(response?.data?.launches?.cursor))) },
        merge = { acc, response -> acc + response.data?.launches?.launches?.filterNotNull().orEmpty() },
        hasMore = { response -> response.data?.launches?.hasMore == true },
    )
    val l = list
    if (l == null) {
        Loading()
    } else {
        // Simple usage (loadMore() is called automatically):
        //
        // LazyColumn {
        //     items(l) { launch ->
        //         LaunchItem(launch = launch, onClick = onLaunchClick)
        //     }
        // }


        // More advanced usage where we want to show a loading indicator, and call loadMore() on demand in the error case:
        val scope = rememberCoroutineScope()
        LazyColumn {
            items(l.items) { launch ->
                LaunchItem(launch = launch, onClick = onLaunchClick)
            }

            item {
                when {
                    l.exception != null -> {
                        ErrorItem(text = "Error: ${l.exception.message}", onClick = { scope.launch { l.loadMore() } })
                    }

                    !l.errors.isNullOrEmpty() -> {
                        ErrorItem(text = "Error: ${l.errors[0].message}", onClick = { scope.launch { l.loadMore() } })
                    }

                    l.hasMore -> {
                        LoadingItem()
                        LaunchedEffect(Unit) {
                            l.loadMore()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchItem(launch: LaunchListQuery.Launch, onClick: (launchId: String) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick(launch.id) },
        headlineText = {
            // Mission name
            Text(text = launch.mission?.name ?: "")
        },
        supportingText = {
            // Site
            Text(text = launch.site ?: "")
        },
        leadingContent = {
            // Mission patch
            AsyncImage(
                modifier = Modifier.size(68.dp, 68.dp),
                model = launch.mission?.missionPatch,
                placeholder = painterResource(R.drawable.ic_placeholder),
                error = painterResource(R.drawable.ic_placeholder),
                contentDescription = "Mission patch"
            )
        }
    )
}

@Composable
private fun ErrorItem(text: String, onClick: () -> Unit) {
    ListItem(
        headlineText = {
            Text(text = text)
        },
        trailingContent = {
            Button(onClick = onClick) {
                Text(text = "Retry")
            }
        }
    )
}


@Composable
private fun LoadingItem() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

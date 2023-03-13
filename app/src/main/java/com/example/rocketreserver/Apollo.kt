package com.example.rocketreserver

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.exception.ApolloException
import com.apollographql.apollo3.network.okHttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private class AuthorizationInterceptor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .apply {
                TokenRepository.getToken()?.let { token ->
                    addHeader("Authorization", token)
                }
            }
            .build()
        return chain.proceed(request)
    }
}

val apolloClient = ApolloClient.Builder()
    .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
    .webSocketServerUrl("wss://apollo-fullstack-tutorial.herokuapp.com/graphql")
    .okHttpClient(
        OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor())
            .build()
    )
    .webSocketReopenWhen { throwable, attempt ->
        Log.d("Apollo", "WebSocket got disconnected, reopening after a delay", throwable)
        delay(attempt * 1000)
        true
    }

    .build()


sealed interface ApolloState<D : Operation.Data> {
    class Loading<D : Operation.Data> : ApolloState<D>
    class Exception<D : Operation.Data>(val exception: ApolloException) : ApolloState<D>
    class Response<D : Operation.Data>(val response: ApolloResponse<D>) : ApolloState<D>
}

@Composable
fun <D : Operation.Data> ApolloCall<D>.toState(context: CoroutineContext = EmptyCoroutineContext): State<ApolloState<D>> {
    val responseFlow = remember {
        toFlow()
            .map<ApolloResponse<D>, ApolloState<D>> { ApolloState.Response(it) }
            .catch { emit(ApolloState.Exception(it as? ApolloException ?: throw it)) }
    }
    return responseFlow.collectAsState(initial = ApolloState.Loading<D>() as ApolloState<D>, context = context)
}


@Composable
fun <D : Operation.Data, T : Any> paginatedList(paginationState: PaginationState<D, T>): State<List<T>> {
    val list = remember { mutableStateOf(emptyList<T>()) }
    val shouldLoadMore by remember { paginationState.shouldLoadMore }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            list.value = paginationState.doLoadMore()
        }
    }
    return list
}

@Composable
fun <D : Operation.Data, T : Any> rememberPaginationState(
    nextCall: (response: ApolloResponse<D>?) -> ApolloCall<D>,
    merge: (acc: List<T>, response: ApolloResponse<D>) -> List<T>,
    hasMore: (response: ApolloResponse<D>) -> Boolean,
): PaginationState<D, T> {
    return remember { PaginationState(nextCall, merge, hasMore) }
}

class PaginationState<D : Operation.Data, T : Any>(
    private val nextCall: (response: ApolloResponse<D>?) -> ApolloCall<D>,
    private val merge: (acc: List<T>, response: ApolloResponse<D>) -> List<T>,
    private val hasMore: (response: ApolloResponse<D>) -> Boolean,
) {
    private var list: List<T> = emptyList()
    private var response: ApolloResponse<D>? = null
    internal val shouldLoadMore = mutableStateOf(true)

    internal suspend fun doLoadMore(): List<T> {
        response = nextCall(response).execute()
        list = merge(list, response!!)
        shouldLoadMore.value = false
        return list
    }

    fun hasMore(): Boolean {
        return response?.let { hasMore(it) } ?: true
    }

    fun loadMore() {
        if (hasMore()) {
            shouldLoadMore.value = true
        }
    }
}

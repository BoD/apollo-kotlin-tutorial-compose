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
import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.exception.ApolloException
import com.apollographql.apollo3.network.okHttpClient
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
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


@Composable
fun <D : Operation.Data> ApolloCall<D>.toState(context: CoroutineContext = EmptyCoroutineContext): State<ApolloResponse<D>?> {
    val responseFlow = remember {
        toFlow()
            .catch { emit(ApolloResponse(this@toState, it as? ApolloException ?: throw it)) }
    }
    return responseFlow.collectAsState(initial = null, context = context)
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
    private val response = mutableStateOf<ApolloResponse<D>?>(null)
    private val shouldLoadMore = mutableStateOf(true)
    private val isLoadingFirstPage = mutableStateOf(true)

    private suspend fun doLoadMore(): List<T> {
        val call = nextCall(response.value)
        response.value = try {
            call.execute()
        } catch (e: ApolloException) {
            ApolloResponse(call = call, exception = e)
        }
        list = merge(list, response.value!!)
        shouldLoadMore.value = false
        if (isLoadingFirstPage.value) isLoadingFirstPage.value = false
        return list
    }

    @Composable
    fun list(): State<List<T>> {
        val list = remember { mutableStateOf(emptyList<T>()) }
        val shouldLoadMore by remember { shouldLoadMore }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) {
                list.value = doLoadMore()
            }
        }
        return list
    }

    @Composable
    fun isLoadingFirstPage(): State<Boolean> {
        return remember { isLoadingFirstPage }
    }

    @Composable
    fun response(): State<ApolloResponse<D>?> {
        return remember { response }
    }

    fun hasMore(): Boolean {
        return response.value?.let { hasMore(it) } ?: true
    }

    fun loadMore() {
        if (hasMore()) {
            shouldLoadMore.value = true
        }
    }
}

val ApolloResponse<*>.exception: ApolloException?
    get() = executionContext[ExceptionElement]?.exception

class ExceptionElement(val exception: ApolloException) : ExecutionContext.Element {
    override val key: ExecutionContext.Key<*> = Key

    companion object Key : ExecutionContext.Key<ExceptionElement>
}

fun <D : Operation.Data> ApolloResponse(call: ApolloCall<D>, exception: ApolloException) =
    ApolloResponse.Builder(operation = call.operation, requestUuid = uuid4(), data = null)
        .addExecutionContext(ExceptionElement(exception))
        .build()

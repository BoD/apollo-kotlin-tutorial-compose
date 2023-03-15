package com.example.rocketreserver

import android.util.Log
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Error
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
fun <D : Operation.Data, T : Any> apolloList(
    nextCall: (response: ApolloResponse<D>?) -> ApolloCall<D>,
    merge: (acc: List<T>, response: ApolloResponse<D>) -> List<T>,
    hasMore: (response: ApolloResponse<D>) -> Boolean,
): State<PaginationState.ApolloList<T>?> {
    return remember { PaginationState(nextCall, merge, hasMore) }.list()
}

class PaginationState<D : Operation.Data, T : Any>(
    private val nextCall: (response: ApolloResponse<D>?) -> ApolloCall<D>,
    private val merge: (acc: List<T>, response: ApolloResponse<D>) -> List<T>,
    private val hasMore: (response: ApolloResponse<D>) -> Boolean,
) {
    class ApolloList<T : Any>(
        private val paginationState: PaginationState<*, T>,
        val items: List<T>,
        val exception: Exception?,
        val errors: List<Error>?,
        val hasMore: Boolean,
    ) {
        suspend fun loadMore() {
            paginationState.loadMore()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ApolloList<*>

            if (items != other.items) return false
            if ((exception == null) != (other.exception == null)) return false
            if (errors != other.errors) return false
            if (hasMore != other.hasMore) return false

            return true
        }

        override fun hashCode(): Int {
            var result = items.hashCode()
            result = 31 * result + (if (exception == null) 0 else 1)
            result = 31 * result + (errors?.hashCode() ?: 0)
            result = 31 * result + hasMore.hashCode()
            return result
        }
    }

    private var response: ApolloResponse<D>? = null
    private val apolloList = mutableStateOf<ApolloList<T>?>(null)

    private suspend fun loadMore() {
        if (apolloList.value?.hasMore != false) {
            doLoadMore()
        }
    }

    private suspend fun doLoadMore() {
        val call = nextCall(response)
        response = call.tryExecute()
        val items = apolloList.value?.items ?: emptyList()
        val mergedItems = merge(items, response!!)
        val hasMore = if (response!!.exception != null || response!!.hasErrors() && items == mergedItems) {
            // Due to an exception or errors, merge could not be done, but there could still be more items
            true
        } else {
            hasMore(response!!)
        }
        apolloList.value = ApolloList(
            paginationState = this,
            items = mergedItems,
            exception = response!!.exception,
            errors = response!!.errors,
            hasMore = hasMore
        )
    }

    /**
     * This is null during initial load.
     */
    @Composable
    fun list(): State<ApolloList<T>?> {
        LaunchedEffect(Unit) {
            loadMore()
        }
        return remember { apolloList }
    }
}

fun <T : Any> LazyListScope.items(
    apolloList: PaginationState.ApolloList<T>,
    key: ((item: T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(value: T) -> Unit,
) {
    items(items = apolloList.items, key = key, itemContent = itemContent)
    item {
        if (apolloList.hasMore) {
            LaunchedEffect(Unit) {
                apolloList.loadMore()
            }
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

suspend fun <D : Operation.Data> ApolloCall<D>.tryExecute(): ApolloResponse<D> = try {
    execute()
} catch (e: ApolloException) {
    ApolloResponse(call = this, exception = e)
}

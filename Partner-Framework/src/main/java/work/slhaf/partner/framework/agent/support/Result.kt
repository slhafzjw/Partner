package work.slhaf.partner.framework.agent.support

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException
import java.util.function.Consumer
import java.util.function.Function

class Result<T> private constructor(
    private val value: T?,
    private val exception: AgentRuntimeException?
) {

    fun getOrThrow(): T {
        if (exception == null) {
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        throw exception
    }

    fun getOrDefault(defaultValue: T): T {
        return if (exception == null) {
            @Suppress("UNCHECKED_CAST")
            value as T
        } else {
            defaultValue
        }
    }

    fun exceptionOrNull(): AgentRuntimeException? = exception

    fun onSuccess(consumer: Consumer<in T>): Result<T> {
        if (exception == null) {
            @Suppress("UNCHECKED_CAST")
            consumer.accept(value as T)
        }
        return this
    }

    fun onFailure(consumer: Consumer<in AgentRuntimeException>): Result<T> {
        if (exception != null) {
            consumer.accept(exception)
        }
        return this
    }

    fun <R> fold(
        onSuccess: Function<in T, out R>,
        onFailure: Function<in AgentRuntimeException, out R>
    ): R {
        return if (exception == null) {
            @Suppress("UNCHECKED_CAST")
            onSuccess.apply(value as T)
        } else {
            onFailure.apply(exception)
        }
    }

    override fun toString(): String {
        return if (exception == null) {
            "Result.success($value)"
        } else {
            "Result.failure($exception)"
        }
    }

    fun interface ThrowingSupplier<T> {
        @Throws(Exception::class)
        fun get(): T
    }

    companion object {
        @JvmStatic
        fun <T> success(value: T): Result<T> = Result(value, null)

        @JvmStatic
        fun <T> failure(exception: AgentRuntimeException): Result<T> = Result(null, exception)

        @JvmStatic
        fun <T> runCatching(block: ThrowingSupplier<T>): Result<T> {
            return try {
                success(block.get())
            } catch (exception: Exception) {
                failure(
                    when (exception) {
                        is AgentRuntimeException -> exception
                        else -> AgentRuntimeException(
                            exception.message ?: "Unexpected runtime failure.",
                            exception
                        )
                    }
                )
            }
        }
    }
}

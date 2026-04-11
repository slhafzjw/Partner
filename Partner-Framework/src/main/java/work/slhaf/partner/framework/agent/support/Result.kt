package work.slhaf.partner.framework.agent.support

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException

class Result<T> private constructor(
    private val value: T?,
    private val exception: Throwable?
) {

    fun isSuccess(): Boolean = exception == null

    fun isFailure(): Boolean = exception != null

    fun getOrNull(): T? = value

    fun exceptionOrNull(): Throwable? = exception

    fun getOrThrow(): T {
        if (exception == null) {
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        when (exception) {
            is AgentRuntimeException, is Error -> throw exception
            else -> throw AgentRuntimeException(exception.localizedMessage, exception)
        }
    }

    fun getOrDefault(defaultValue: T): T {
        return if (exception == null) {
            @Suppress("UNCHECKED_CAST")
            value as T
        } else {
            defaultValue
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
        @Throws(Throwable::class)
        fun get(): T
    }

    companion object {
        @JvmStatic
        fun <T> success(value: T): Result<T> = Result(value, null)

        @JvmStatic
        fun <T> failure(exception: Throwable): Result<T> = Result(null, exception)

        @JvmStatic
        fun <T> runCatching(block: ThrowingSupplier<T>): Result<T> {
            return try {
                success(block.get())
            } catch (throwable: Throwable) {
                failure(
                    when (throwable) {
                        is AgentRuntimeException, is Error -> throwable
                        else -> AgentRuntimeException(
                            throwable.message ?: "Unexpected runtime failure.",
                            throwable
                        )
                    }
                )
            }
        }
    }
}

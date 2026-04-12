package work.slhaf.partner.framework.agent.support;

import org.junit.jupiter.api.Test;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void shouldKeepAgentRuntimeExceptionInFailure() {
        AgentRuntimeException exception = new AgentRuntimeException("runtime failure");

        Result<String> result = Result.failure(exception);

        assertSame(exception, result.exceptionOrNull());
        AtomicReference<AgentRuntimeException> failure = new AtomicReference<>();
        result.onFailure(failure::set);
        assertSame(exception, failure.get());
        AgentRuntimeException thrown = assertThrows(AgentRuntimeException.class, result::getOrThrow);
        assertSame(exception, thrown);
    }

    @Test
    void shouldWrapCheckedExceptionInRunCatching() {
        Result<String> result = Result.runCatching(() -> {
            throw new java.io.IOException("io failure");
        });

        assertNotNull(result.exceptionOrNull());
        assertInstanceOf(java.io.IOException.class, result.exceptionOrNull().getCause());
        AgentRuntimeException thrown = assertThrows(AgentRuntimeException.class, result::getOrThrow);
        assertInstanceOf(java.io.IOException.class, thrown.getCause());
    }

    @Test
    void shouldKeepAgentRuntimeExceptionInRunCatching() {
        AgentRuntimeException exception = new AgentRuntimeException("runtime failure");

        Result<String> result = Result.runCatching(() -> {
            throw exception;
        });

        assertSame(exception, result.exceptionOrNull());
        AgentRuntimeException thrown = assertThrows(AgentRuntimeException.class, result::getOrThrow);
        assertSame(exception, thrown);
    }

    @Test
    void shouldReturnNullExceptionForSuccess() {
        assertNull(Result.success("ok").exceptionOrNull());
    }

    @Test
    void shouldNotCatchErrorInRunCatching() {
        AssertionError error = new AssertionError("boom");

        AssertionError thrown = assertThrows(AssertionError.class, () -> Result.runCatching(() -> {
            throw error;
        }));

        assertSame(error, thrown);
    }

    @Test
    void shouldHandleSuccessAndFailureBranchesWithChainApis() {
        AtomicReference<String> successValue = new AtomicReference<>();
        AtomicReference<AgentRuntimeException> failureValue = new AtomicReference<>();
        AgentRuntimeException exception = new AgentRuntimeException("runtime failure");

        Result.success("ok")
                .onSuccess(successValue::set)
                .onFailure(failureValue::set);

        assertEquals("ok", successValue.get());
        assertNull(failureValue.get());

        successValue.set(null);
        Result.<String>failure(exception)
                .onSuccess(successValue::set)
                .onFailure(failureValue::set);

        assertNull(successValue.get());
        assertSame(exception, failureValue.get());
    }

    @Test
    void shouldFoldResult() {
        String success = Result.success("ok").fold(
                value -> "success:" + value,
                ex -> "failure:" + ex.getMessage()
        );
        String failure = Result.<String>failure(new AgentRuntimeException("bad")).fold(
                value -> "success:" + value,
                ex -> "failure:" + ex.getMessage()
        );

        assertEquals("success:ok", success);
        assertEquals("failure:bad", failure);
    }
}

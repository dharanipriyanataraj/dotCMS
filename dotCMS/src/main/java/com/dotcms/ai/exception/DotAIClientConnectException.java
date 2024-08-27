package com.dotcms.ai.exception;

import com.dotmarketing.exception.DotRuntimeException;

/**
 * Exception thrown when there is a connection error with the AI client.
 *
 * <p>
 * This exception is used to indicate that there is a connection error with the AI client. It extends the {@link DotRuntimeException}
 * to provide additional context specific to AI client connection error scenarios.
 * </p>
 *
 * @author vico
 */
public class DotAIClientConnectException extends DotRuntimeException {

    public DotAIClientConnectException(final String message, final Throwable cause) {
        super(message, cause);
    }

}

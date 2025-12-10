package io.brokr.api.config;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import io.brokr.core.exception.AccessDeniedException;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.UnauthorizedException;
import io.brokr.core.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import graphql.schema.DataFetchingEnvironment;

/**
 * GraphQL exception resolver that maps application exceptions to proper GraphQL error responses.
 * This ensures that custom exceptions like ValidationException are properly returned to the client
 * with meaningful error messages instead of generic INTERNAL_ERROR.
 */
@Slf4j
@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ValidationException) {
            log.debug("Validation error: {}", ex.getMessage());
            return GraphqlErrorBuilder.newError()
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(ex.getMessage())
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }

        if (ex instanceof ResourceNotFoundException) {
            log.debug("Resource not found: {}", ex.getMessage());
            return GraphqlErrorBuilder.newError()
                    .errorType(ErrorType.NOT_FOUND)
                    .message(ex.getMessage())
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }

        if (ex instanceof UnauthorizedException) {
            log.debug("Unauthorized: {}", ex.getMessage());
            return GraphqlErrorBuilder.newError()
                    .errorType(ErrorType.UNAUTHORIZED)
                    .message(ex.getMessage())
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }

        if (ex instanceof AccessDeniedException) {
            log.debug("Access denied: {}", ex.getMessage());
            return GraphqlErrorBuilder.newError()
                    .errorType(ErrorType.FORBIDDEN)
                    .message("You do not have permission to access this resource.")
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }

        if (ex instanceof org.springframework.security.access.AccessDeniedException) {
            log.debug("Access denied: {}", ex.getMessage());
            return GraphqlErrorBuilder.newError()
                    .errorType(ErrorType.FORBIDDEN)
                    .message("You do not have permission to access this resource.")
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }

        if (ex instanceof DataIntegrityViolationException) {
            log.warn("Data integrity violation: {}", ex.getMessage());
            return GraphqlErrorBuilder.newError()
                    .errorType(ErrorType.BAD_REQUEST)
                    .message("A database constraint was violated. This may be due to a duplicate name or other invalid data.")
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }

        if (ex instanceof BadCredentialsException) {
            log.debug("Bad credentials: {}", ex.getMessage());
            return GraphqlErrorBuilder.newError()
                    .errorType(ErrorType.UNAUTHORIZED)
                    .message("Invalid username or password")
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }

        // For all other exceptions, log the error and return a generic message
        log.error("Unhandled exception in GraphQL resolver", ex);
        return GraphqlErrorBuilder.newError()
                .errorType(ErrorType.INTERNAL_ERROR)
                .message("An unexpected error occurred. Please contact support.")
                .path(env.getExecutionStepInfo().getPath())
                .build();
    }
}


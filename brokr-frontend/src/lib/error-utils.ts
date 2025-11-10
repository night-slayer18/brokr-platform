/**
 * Utility functions for extracting and formatting error messages from GraphQL and network errors
 */

export interface GraphQLError {
    message: string;
    locations?: Array<{ line: number; column: number }>;
    path?: Array<string | number>;
    extensions?: Record<string, unknown>;
}

/**
 * Extracts a user-friendly error message from a GraphQL error response
 */
export function extractErrorMessage(error: unknown): string {
    // Handle GraphQL errors
    if (
        typeof error === 'object' &&
        error !== null &&
        'response' in error &&
        typeof (error as { response: unknown }).response === 'object' &&
        (error as { response: { errors?: unknown } }).response !== null &&
        'errors' in (error as { response: { errors?: unknown } }).response
    ) {
        const response = (error as { response: { errors?: unknown } }).response;
        if (Array.isArray(response.errors)) {
            const graphqlErrors = response.errors as GraphQLError[];
            // Return the first error message
            const firstError = graphqlErrors[0];
            if (firstError?.message) {
                return firstError.message;
            }
        }
    }

    // Handle error objects with a message property
    if (typeof error === 'object' && error !== null && 'message' in error) {
        const message = (error as { message: unknown }).message;
        if (typeof message === 'string') {
            // Check if it's a network error
            if (message.includes('Network') || message.includes('fetch')) {
                return 'Network error. Please check your connection and try again.';
            }
            return message;
        }
    }

    // Handle string errors
    if (typeof error === 'string') {
        return error;
    }

    // Default fallback
    return 'An unexpected error occurred. Please try again.';
}

/**
 * Extracts all error messages from a GraphQL error response
 */
export function extractAllErrorMessages(error: unknown): string[] {
    const messages: string[] = [];

    // Handle GraphQL errors
    if (
        typeof error === 'object' &&
        error !== null &&
        'response' in error &&
        typeof (error as { response: unknown }).response === 'object' &&
        (error as { response: { errors?: unknown } }).response !== null &&
        'errors' in (error as { response: { errors?: unknown } }).response
    ) {
        const response = (error as { response: { errors?: unknown } }).response;
        if (Array.isArray(response.errors)) {
            const graphqlErrors = response.errors as GraphQLError[];
            graphqlErrors.forEach((err) => {
                if (err?.message) {
                    messages.push(err.message);
                }
            });
        }
    } else if (typeof error === 'object' && error !== null && 'message' in error) {
        const message = (error as { message: unknown }).message;
        if (typeof message === 'string') {
            messages.push(message);
        }
    } else if (typeof error === 'string') {
        messages.push(error);
    }

    return messages.length > 0 ? messages : ['An unexpected error occurred. Please try again.'];
}


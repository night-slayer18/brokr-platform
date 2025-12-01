import {GraphQLClient} from 'graphql-request';
import type {DocumentNode} from 'graphql';
import {print} from 'graphql';
import {extractErrorMessage} from './error-utils';
import {useAuthStore} from '@/store/authStore';

// Get GraphQL endpoint URL - handle both dev and production environments
function getGraphQLEndpoint(): string {
    // In production (served from backend), use relative URL
    // In development, use environment variable or default to relative URL
    const envEndpoint = import.meta.env.VITE_GRAPHQL_ENDPOINT;
    
    // If environment variable is set and is a valid non-empty string
    if (envEndpoint && typeof envEndpoint === 'string') {
        const trimmed = envEndpoint.trim();
        if (trimmed === '') {
            // Empty string, use default
            return '/graphql';
        }
        
        // Check if it's already a full URL (starts with http:// or https://)
        if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
            try {
                // Validate it's a proper URL
                new URL(trimmed);
                return trimmed;
            } catch {
                // Invalid URL, fall back to default
                console.warn(`Invalid GraphQL endpoint URL: ${trimmed}, using default /graphql`);
                return '/graphql';
            }
        }
        
        // If it's a relative path, ensure it starts with /
        return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
    }
    
    // Default to relative path when served from backend
    return '/graphql';
}

// Create GraphQL client with error handling
// Use lazy initialization to ensure window.location is available
let graphqlClientInstance: GraphQLClient | null = null;

function createGraphQLClient(): GraphQLClient {
    // If client already exists, return it
    if (graphqlClientInstance) {
        return graphqlClientInstance;
    }
    
    const endpoint = getGraphQLEndpoint();
    
    // Ensure we have a valid endpoint string
    if (!endpoint || typeof endpoint !== 'string' || endpoint.trim() === '') {
        console.error('Invalid GraphQL endpoint, using default /graphql');
        const defaultEndpoint = '/graphql';
        // Construct absolute URL from relative path
        if (typeof window !== 'undefined' && window.location && window.location.origin) {
            try {
                const absoluteUrl = new URL(defaultEndpoint, window.location.origin).toString();
                graphqlClientInstance = new GraphQLClient(absoluteUrl, {
                    credentials: 'same-origin',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                return graphqlClientInstance;
            } catch (error) {
                console.error('Failed to construct default GraphQL URL:', error);
            }
        }
        // Fallback: try relative URL (should work in browser)
        graphqlClientInstance = new GraphQLClient(defaultEndpoint, {
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json',
            },
        });
        return graphqlClientInstance;
    }
    
    // Convert relative URLs to absolute URLs
    let finalEndpoint = endpoint;
    if (endpoint.startsWith('/')) {
        // For relative URLs, construct absolute URL using current origin
        if (typeof window !== 'undefined' && window.location && window.location.origin) {
            try {
                finalEndpoint = new URL(endpoint, window.location.origin).toString();
            } catch (error) {
                console.error('Failed to construct absolute URL from relative path:', error);
                // If URL construction fails, keep the relative path
                // graphql-request should handle relative URLs in browser context
            }
        }
    }
    
    // Create client with the final endpoint (should be absolute URL now)
    try {
        graphqlClientInstance = new GraphQLClient(finalEndpoint, {
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json',
            },
        });
        return graphqlClientInstance;
    } catch (error) {
        console.error('Failed to create GraphQL client:', error);
        // Last resort: try to construct absolute URL from default
        if (typeof window !== 'undefined' && window.location && window.location.origin) {
            try {
                const defaultAbsoluteUrl = new URL('/graphql', window.location.origin).toString();
                graphqlClientInstance = new GraphQLClient(defaultAbsoluteUrl, {
                    credentials: 'same-origin',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                return graphqlClientInstance;
            } catch (fallbackError) {
                console.error('Failed to create fallback GraphQL client:', fallbackError);
            }
        }
        // Ultimate fallback
        graphqlClientInstance = new GraphQLClient('/graphql', {
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json',
            },
        });
        return graphqlClientInstance;
    }
}

// Export a getter function that creates the client lazily
export const graphqlClient = new Proxy({} as GraphQLClient, {
    get(_target, prop) {
        const client = createGraphQLClient();
        const value = (client as unknown as Record<string, unknown>)[prop as string];
        if (typeof value === 'function') {
            return value.bind(client);
        }
        return value;
    }
})

// Global flag to prevent multiple simultaneous logout attempts
// when multiple GraphQL queries fail with 401 at the same time
let isLoggingOut = false

// Helper function to execute GraphQL requests with error handling
async function executeRequest<TData = unknown, TVariables = unknown>(
    document: DocumentNode | string,
    variables?: TVariables
): Promise<TData> {
    try {
        const queryString = typeof document === 'string' ? document : print(document);
        // graphql-request expects variables to be an object or undefined
        const vars = (variables ?? {}) as Record<string, unknown>;
        return await graphqlClient.request<TData, Record<string, unknown>>(queryString, vars);
    } catch (error: unknown) {
        // Handle GraphQL errors
        if (error && typeof error === 'object' && 'response' in error) {
            const response = (error as { response?: { errors?: Array<{ message?: string; locations?: unknown; path?: unknown }> } }).response;
            if (response?.errors) {
                const graphqlErrors = response.errors;
                graphqlErrors.forEach((err) => {
                    console.error(
                        `[GraphQL error]: Message: ${err.message}, Location: ${err.locations}, Path: ${err.path}`
                    );

                    // Handle unauthorized errors (expired token or invalid session)
                    if (err.message?.includes('Unauthorized') || err.message?.includes('401') || err.message?.includes('authentication') || err.message?.includes('Forbidden')) {
                        // Prevent multiple logout attempts
                        if (isLoggingOut) {
                            return // Already logging out, skip
                        }
                        
                        isLoggingOut = true
                        const authStore = useAuthStore.getState();
                        
                        // Ensure logout completes before redirect
                        authStore.logout().then(() => {
                            if (typeof window !== 'undefined') {
                                window.location.href = '/login';
                            }
                        }).catch((logoutError) => {
                            console.error('Logout failed during 401 handling:', logoutError);
                            if (typeof window !== 'undefined') {
                                window.location.href = '/login';
                            }
                        }).finally(() => {
                            // Reset flag after redirect (might not execute due to navigation)
                            isLoggingOut = false
                        });
                        
                        throw new Error('Your session has expired. Please log in again.');
                    }
                });
            }
        } else if (error && typeof error === 'object' && 'message' in error && typeof (error as { message: unknown }).message === 'string') {
            console.error(`[Network error]: ${(error as { message: string }).message}`);
        }

        // Create a new error with extracted message for better error handling
        const errorMessage = extractErrorMessage(error);
        const enhancedError = new Error(errorMessage);
        // Preserve original error for debugging
        (enhancedError as Error & { originalError?: unknown }).originalError = error;
        throw enhancedError;
    }
}

// Helper function to execute GraphQL requests
export async function executeGraphQL<TData = unknown, TVariables = unknown>(
    query: DocumentNode | string,
    variables?: TVariables
): Promise<TData> {
    return executeRequest<TData, TVariables>(query, variables);
}


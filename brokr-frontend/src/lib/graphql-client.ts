import {GraphQLClient} from 'graphql-request';
import type {DocumentNode} from 'graphql';
import {print} from 'graphql';

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
        const value = (client as any)[prop];
        if (typeof value === 'function') {
            return value.bind(client);
        }
        return value;
    },
});

// Helper function to execute GraphQL requests with error handling
async function executeRequest<TData = any, TVariables = any>(
    document: DocumentNode | string,
    variables?: TVariables
): Promise<TData> {
    try {
        const queryString = typeof document === 'string' ? document : print(document);
        // graphql-request expects variables to be an object or undefined
        const vars = (variables ?? {}) as Record<string, any>;
        return await graphqlClient.request<TData, Record<string, any>>(queryString, vars);
    } catch (error: any) {
        // Handle GraphQL errors
        if (error.response?.errors) {
            const graphqlErrors = error.response.errors;
            graphqlErrors.forEach((err: any) => {
                console.error(
                    `[GraphQL error]: Message: ${err.message}, Location: ${err.locations}, Path: ${err.path}`
                );

                // Handle unauthorized errors
                if (err.message.includes('Unauthorized') || err.message.includes('401')) {
                    // Clear auth state and redirect to login
                    window.location.href = '/login';
                    return;
                }
            });
        } else if (error.message) {
            console.error(`[Network error]: ${error.message}`);
        }

        throw error;
    }
}

// Helper function to execute GraphQL requests
export async function executeGraphQL<TData = any, TVariables = any>(
    query: DocumentNode | string,
    variables?: TVariables
): Promise<TData> {
    return executeRequest<TData, TVariables>(query, variables);
}


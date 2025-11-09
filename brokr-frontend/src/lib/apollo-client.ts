import {ApolloClient, from, HttpLink, InMemoryCache,} from "@apollo/client";
import {ErrorLink} from "@apollo/client/link/error";
import {CombinedGraphQLErrors, CombinedProtocolErrors} from "@apollo/client/errors";

const httpLink = new HttpLink({
    // Use relative URL when served from backend, absolute URL for dev mode
    uri: import.meta.env.VITE_GRAPHQL_ENDPOINT ?? "/graphql",
    credentials: "same-origin", // Include cookies (HttpOnly cookie with JWT token)
});

// No need for authLink anymore - JWT token is in HttpOnly cookie and sent automatically
// The backend JwtAuthenticationFilter reads from the cookie

const errorLink = new ErrorLink(({error, operation}) => {
    if (CombinedGraphQLErrors.is(error)) {
        error.errors.forEach(({message, locations, path}) => {
            console.error(
                `[GraphQL error]: Message: ${message}, Location: ${locations}, Path: ${path}, Operation: ${operation.operationName}`
            );
            if (message.includes("Unauthorized") || message.includes("401")) {
                // Clear auth state and redirect to login
                // Cookie will be cleared by backend on logout
                window.location.href = "/login";
            }
        });
    } else if (CombinedProtocolErrors.is(error)) {
        error.errors.forEach(({message, extensions}) => {
            console.error(
                `[Protocol error]: Message: ${message}, Extensions: ${JSON.stringify(
                    extensions
                )}, Operation: ${operation.operationName}`
            );
        });
    }
});

export const apolloClient = new ApolloClient({
    link: from([errorLink, httpLink]),
    cache: new InMemoryCache(),
    defaultOptions: {
        watchQuery: {
            // Use cache-first for better performance: show cached data immediately, then update in background
            fetchPolicy: "cache-first",
            // Ensure subsequent queries also use cache (prevents unnecessary network requests)
            nextFetchPolicy: "cache-first",
        },
        query: {
            // For one-time queries, also prefer cache
            fetchPolicy: "cache-first",
        },
    },
});

import {ApolloClient, from, HttpLink, InMemoryCache,} from "@apollo/client";
import {SetContextLink} from "@apollo/client/link/context";
import {ErrorLink} from "@apollo/client/link/error";
import {CombinedGraphQLErrors, CombinedProtocolErrors} from "@apollo/client/errors";

const httpLink = new HttpLink({
    // Use relative URL when served from backend, absolute URL for dev mode
    uri: import.meta.env.VITE_GRAPHQL_ENDPOINT ?? "/graphql",
    credentials: "same-origin",
});

const authLink = new SetContextLink((prevContext) => {
    const token = localStorage.getItem("brokr_token");
    return {
        headers: {
            ...prevContext.headers,
            authorization: token ? `Bearer ${token}` : "",
        },
    };
});

const errorLink = new ErrorLink(({error, operation}) => {
    if (CombinedGraphQLErrors.is(error)) {
        error.errors.forEach(({message, locations, path}) => {
            console.error(
                `[GraphQL error]: Message: ${message}, Location: ${locations}, Path: ${path}, Operation: ${operation.operationName}`
            );
            if (message.includes("Unauthorized") || message.includes("401")) {
                localStorage.removeItem("brokr_token");
                localStorage.removeItem("brokr_user");
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
    link: from([errorLink, authLink, httpLink]),
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

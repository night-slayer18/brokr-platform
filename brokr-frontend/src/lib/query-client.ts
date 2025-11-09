import { QueryClient } from '@tanstack/react-query';

// Create QueryClient with optimized settings for heavy load
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Stale time: data is considered fresh for 30 seconds
      staleTime: 30 * 1000,
      // Cache time: data stays in cache for 5 minutes
      gcTime: 5 * 60 * 1000, // Previously cacheTime
      // Retry failed requests 2 times
      retry: 2,
      // Retry delay increases exponentially
      retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
      // Refetch on window focus (but only if data is stale)
      refetchOnWindowFocus: true,
      // Refetch on reconnect
      refetchOnReconnect: true,
      // Don't refetch on mount if data exists
      refetchOnMount: true,
      // Use network-first strategy for better UX
      networkMode: 'online',
    },
    mutations: {
      // Retry failed mutations once
      retry: 1,
      // Retry delay
      retryDelay: 1000,
      // Network mode
      networkMode: 'online',
    },
  },
});


import { QueryClient } from '@tanstack/react-query';

// Create QueryClient with optimized settings for heavy load
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Stale time: data is considered fresh for 30 seconds
      staleTime: 30 * 1000,
      // Cache time: data stays in cache for 5 minutes
      gcTime: 5 * 60 * 1000, // Previously cacheTime
      // Retry failed requests 2 times, BUT skip auth errors (401/403)
      retry: (failureCount, error) => {
        // Don't retry on auth errors - they won't fix themselves
        if (error instanceof Error) {
          const message = error.message.toLowerCase()
          if (message.includes('unauthorized') || 
              message.includes('401') || 
              message.includes('403') ||
              message.includes('forbidden') ||
              message.includes('session expired')) {
            return false // No retry on auth errors
          }
        }
        // Retry other errors up to 2 times
        return failureCount < 2
      },
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
      // Don't retry mutations on auth errors
      retry: (failureCount, error) => {
        if (error instanceof Error) {
          const message = error.message.toLowerCase()
          if (message.includes('unauthorized') || 
              message.includes('401') || 
              message.includes('403') ||
              message.includes('forbidden') ||
              message.includes('session expired')) {
            return false
          }
        }
        // Retry other mutation errors once
        return failureCount < 1
      },
      // Retry delay
      retryDelay: 1000,
      // Network mode
      networkMode: 'online',
    },
  },
});


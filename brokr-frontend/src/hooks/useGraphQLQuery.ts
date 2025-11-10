import { useQuery } from '@tanstack/react-query';
import type { UseQueryOptions, UseQueryResult } from '@tanstack/react-query';
import { executeGraphQL } from '@/lib/graphql-client';
import { extractErrorMessage } from '@/lib/error-utils';
import { toast } from 'sonner';
import type { DocumentNode } from 'graphql';
import { print } from 'graphql';
import { useEffect } from 'react';

interface QueryOptionsWithError<TData> extends Omit<UseQueryOptions<TData, Error, TData, readonly unknown[]>, 'queryKey' | 'queryFn'> {
  onError?: (error: Error) => void;
  disableErrorNotification?: boolean;
}

export function useGraphQLQuery<TData = any, TVariables = any>(
  query: DocumentNode | string,
  variables?: TVariables,
  options?: QueryOptionsWithError<TData>
): UseQueryResult<TData, Error> {
  const queryString = typeof query === 'string' ? query : print(query);
  // Create a stable query key that includes the query string and variables
  const queryKey = ['graphql', queryString, variables] as const;

  const userOnError = options?.onError;
  const disableErrorNotification = options?.disableErrorNotification;

  const queryResult = useQuery<TData, Error>({
    queryKey,
    queryFn: () => executeGraphQL<TData, TVariables>(query, variables),
    ...options,
  });

  // Handle errors with toast notifications
  useEffect(() => {
    if (queryResult.error && !userOnError && !disableErrorNotification) {
      const errorMessage = extractErrorMessage(queryResult.error);
      toast.error(errorMessage);
    }
  }, [queryResult.error, userOnError, disableErrorNotification]);

  // Call user's onError callback if provided
  useEffect(() => {
    if (queryResult.error && userOnError) {
      userOnError(queryResult.error);
    }
  }, [queryResult.error, userOnError]);

  return queryResult;
}


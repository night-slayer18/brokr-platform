import { useQuery } from '@tanstack/react-query';
import type { UseQueryOptions, UseQueryResult } from '@tanstack/react-query';
import { executeGraphQL } from '@/lib/graphql-client';
import type { DocumentNode } from 'graphql';
import { print } from 'graphql';

export function useGraphQLQuery<TData = any, TVariables = any>(
  query: DocumentNode | string,
  variables?: TVariables,
  options?: Omit<UseQueryOptions<TData, Error, TData, readonly unknown[]>, 'queryKey' | 'queryFn'>
): UseQueryResult<TData, Error> {
  const queryString = typeof query === 'string' ? query : print(query);
  // Create a stable query key that includes the query string and variables
  const queryKey = ['graphql', queryString, variables] as const;

  return useQuery<TData, Error>({
    queryKey,
    queryFn: () => executeGraphQL<TData, TVariables>(query, variables),
    ...options,
  });
}


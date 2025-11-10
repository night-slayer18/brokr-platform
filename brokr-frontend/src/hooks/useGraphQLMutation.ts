import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { UseMutationOptions, UseMutationResult } from '@tanstack/react-query';
import { executeGraphQL } from '@/lib/graphql-client';
import { extractErrorMessage } from '@/lib/error-utils';
import { toast } from 'sonner';
import type { DocumentNode } from 'graphql';
import { print } from 'graphql';

export function useGraphQLMutation<TData = any, TVariables = any>(
  mutation: DocumentNode | string,
  options?: Omit<UseMutationOptions<TData, Error, TVariables, unknown>, 'mutationFn'>
): UseMutationResult<TData, Error, TVariables> {
  const queryClient = useQueryClient();

  const userOnError = options?.onError;
  const userOnSuccess = options?.onSuccess;

  const mutationOptions: UseMutationOptions<TData, Error, TVariables, unknown> = {
    mutationFn: (variables: TVariables) =>
      executeGraphQL<TData, TVariables>(mutation, variables),
    ...options,
    onSuccess: (data, variables, context, mutationInstance) => {
      // Invalidate only relevant queries after successful mutation
      // This prevents unnecessary refetches of unrelated queries
      const mutationString = typeof mutation === 'string' ? mutation : print(mutation);
      
      // Invalidate queries that might be affected by this mutation
      // Check if mutation contains keywords to determine what to invalidate
      if (mutationString.includes('cluster') || mutationString.includes('Cluster')) {
        queryClient.invalidateQueries({ 
          predicate: (query) => {
            const queryKey = query.queryKey[0];
            const queryString = query.queryKey[1]?.toString() || '';
            return queryKey === 'graphql' && 
                   (queryString.includes('cluster') || 
                    queryString.includes('Cluster'));
          }
        });
      } else if (mutationString.includes('user') || mutationString.includes('User')) {
        queryClient.invalidateQueries({ 
          predicate: (query) => {
            const queryKey = query.queryKey[0];
            const queryString = query.queryKey[1]?.toString() || '';
            return queryKey === 'graphql' && 
                   (queryString.includes('user') || 
                    queryString.includes('User') ||
                    queryString.includes('organization') ||
                    queryString.includes('Organization'));
          }
        });
      } else if (mutationString.includes('organization') || mutationString.includes('Organization')) {
        queryClient.invalidateQueries({ 
          predicate: (query) => {
            const queryKey = query.queryKey[0];
            const queryString = query.queryKey[1]?.toString() || '';
            return queryKey === 'graphql' && 
                   (queryString.includes('organization') || 
                    queryString.includes('Organization'));
          }
        });
      } else {
        // Fallback: invalidate all GraphQL queries if we can't determine the type
        queryClient.invalidateQueries({ 
          predicate: (query) => query.queryKey[0] === 'graphql'
        });
      }
      
      if (userOnSuccess) {
        // Call the user's onSuccess callback if provided
        userOnSuccess(data, variables, context, mutationInstance);
      }
    },
    onError: (error, variables, context, mutationInstance) => {
      // Extract user-friendly error message
      const errorMessage = extractErrorMessage(error);
      
      // Show toast notification by default (unless user provides their own onError)
      if (!userOnError) {
        toast.error(errorMessage);
      }
      
      // Call user's onError callback if provided
      if (userOnError) {
        userOnError(error, variables, context, mutationInstance);
      }
    },
  };

  return useMutation<TData, Error, TVariables, unknown>(mutationOptions);
}


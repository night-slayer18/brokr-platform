import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { UseMutationOptions, UseMutationResult } from '@tanstack/react-query';
import { executeGraphQL } from '@/lib/graphql-client';
import type { DocumentNode } from 'graphql';

export function useGraphQLMutation<TData = any, TVariables = any>(
  mutation: DocumentNode | string,
  options?: Omit<UseMutationOptions<TData, Error, TVariables, unknown>, 'mutationFn'>
): UseMutationResult<TData, Error, TVariables> {
  const queryClient = useQueryClient();

  const mutationOptions: UseMutationOptions<TData, Error, TVariables, unknown> = {
    mutationFn: (variables: TVariables) =>
      executeGraphQL<TData, TVariables>(mutation, variables),
    ...options,
    onSuccess: (data, variables, context) => {
      // Invalidate relevant queries after successful mutation
      queryClient.invalidateQueries();
      if (options?.onSuccess) {
        // Call the user's onSuccess callback if provided
        (options.onSuccess as any)(data, variables, context);
      }
    },
  };

  return useMutation<TData, Error, TVariables, unknown>(mutationOptions);
}


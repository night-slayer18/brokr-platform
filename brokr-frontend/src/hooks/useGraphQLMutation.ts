import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { UseMutationOptions, UseMutationResult } from '@tanstack/react-query';
import { executeGraphQL } from '@/lib/graphql-client';
import { extractErrorMessage } from '@/lib/error-utils';
import { toast } from 'sonner';
import type { DocumentNode } from 'graphql';

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
      // Invalidate relevant queries after successful mutation
      queryClient.invalidateQueries();
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


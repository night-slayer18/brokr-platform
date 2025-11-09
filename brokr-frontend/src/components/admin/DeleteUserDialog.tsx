import {useState} from 'react'
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {toast} from 'sonner'
import {DELETE_USER_MUTATION} from '@/graphql/mutations'
import type {DeleteUserMutation, GetOrganizationQuery} from '@/graphql/types'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {useQueryClient} from '@tanstack/react-query'
import {GET_ORGANIZATION, GET_ORGANIZATIONS, GET_USERS, GET_USER} from '@/graphql/queries'

interface DeleteUserDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    user: NonNullable<GetOrganizationQuery['organization']['users']>[0]
}

export function DeleteUserDialog({open, onOpenChange, user}: DeleteUserDialogProps) {
    const [isDeleting, setIsDeleting] = useState(false)
    const queryClient = useQueryClient()
    const {mutate: deleteUser} = useGraphQLMutation<DeleteUserMutation, {id: string}>(DELETE_USER_MUTATION, {
        onSuccess: () => {
            toast.success('User deleted successfully')
            queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATION]})
            queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATIONS]})
            queryClient.invalidateQueries({queryKey: ['graphql', GET_USERS]})
            queryClient.invalidateQueries({queryKey: ['graphql', GET_USER]})
            onOpenChange(false)
        },
        onError: (error) => {
            toast.error(error.message || 'Failed to delete user')
            setIsDeleting(false)
        },
    })

    const handleDelete = () => {
        setIsDeleting(true)
        deleteUser({id: user.id})
    }

    const userName = user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : user.username

    return (
        <AlertDialog open={open} onOpenChange={onOpenChange}>
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogTitle>Are you sure?</AlertDialogTitle>
                    <AlertDialogDescription>
                        This will permanently delete the user <strong>{userName}</strong> ({user.email}). This action
                        cannot be undone.
                    </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
                    <AlertDialogAction
                        onClick={handleDelete}
                        disabled={isDeleting}
                        className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                    >
                        {isDeleting ? 'Deleting...' : 'Delete'}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    )
}


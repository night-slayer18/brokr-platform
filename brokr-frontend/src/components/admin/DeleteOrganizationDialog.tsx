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
import {DELETE_ORGANIZATION_MUTATION} from '@/graphql/mutations'
import type {DeleteOrganizationMutation, GetOrganizationsQuery} from '@/graphql/types'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {useQueryClient} from '@tanstack/react-query'
import {GET_ORGANIZATIONS, GET_ORGANIZATION} from '@/graphql/queries'
import {useNavigate} from 'react-router-dom'

interface DeleteOrganizationDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    organization: GetOrganizationsQuery['organizations'][0]
}

export function DeleteOrganizationDialog({open, onOpenChange, organization}: DeleteOrganizationDialogProps) {
    const [isDeleting, setIsDeleting] = useState(false)
    const queryClient = useQueryClient()
    const navigate = useNavigate()
    const {mutate: deleteOrganization} = useGraphQLMutation<DeleteOrganizationMutation, {id: string}>(
        DELETE_ORGANIZATION_MUTATION,
        {
            onSuccess: () => {
                toast.success('Organization deleted successfully')
                queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATIONS]})
                queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATION]})
                onOpenChange(false)
                navigate('/admin/organizations')
            },
            onError: (error) => {
                toast.error(error.message || 'Failed to delete organization')
                setIsDeleting(false)
            },
        }
    )

    const handleDelete = () => {
        setIsDeleting(true)
        deleteOrganization({id: organization.id})
    }

    return (
        <AlertDialog open={open} onOpenChange={onOpenChange}>
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogTitle>Are you sure?</AlertDialogTitle>
                    <AlertDialogDescription>
                        This will permanently delete the organization <strong>{organization.name}</strong>. This action
                        cannot be undone and will also delete all associated users, environments, and clusters.
                    </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
                    <AlertDialogAction onClick={handleDelete} disabled={isDeleting} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
                        {isDeleting ? 'Deleting...' : 'Delete'}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    )
}


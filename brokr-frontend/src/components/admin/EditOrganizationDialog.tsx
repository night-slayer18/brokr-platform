import {useEffect, useState} from 'react'
import {useForm, Controller} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Textarea} from '@/components/ui/textarea'
import {Switch} from '@/components/ui/switch'
import {toast} from 'sonner'
import {UPDATE_ORGANIZATION_MUTATION} from '@/graphql/mutations'
import type {UpdateOrganizationMutation, GetOrganizationsQuery} from '@/graphql/types'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {useQueryClient} from '@tanstack/react-query'
import {GET_ORGANIZATIONS, GET_ORGANIZATION} from '@/graphql/queries'

const organizationSchema = z.object({
    name: z.string().min(1, 'Name is required'),
    description: z.string().optional().or(z.literal('')),
    isActive: z.boolean(),
})

type OrganizationFormData = z.infer<typeof organizationSchema>

interface EditOrganizationDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    organization: GetOrganizationsQuery['organizations'][0]
}

export function EditOrganizationDialog({open, onOpenChange, organization}: EditOrganizationDialogProps) {
    const [isSubmitting, setIsSubmitting] = useState(false)
    const queryClient = useQueryClient()
    const {mutate: updateOrganization} = useGraphQLMutation<
        UpdateOrganizationMutation,
        {id: string; input: OrganizationFormData}
    >(UPDATE_ORGANIZATION_MUTATION, {
        onSuccess: () => {
            toast.success('Organization updated successfully')
            queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATIONS]})
            queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATION]})
            onOpenChange(false)
        },
        onError: (error) => {
            toast.error(error.message || 'Failed to update organization')
            setIsSubmitting(false)
        },
    })

    const {
        register,
        handleSubmit,
        reset,
        control,
        formState: {errors},
    } = useForm<OrganizationFormData>({
        resolver: zodResolver(organizationSchema),
        defaultValues: {
            name: organization.name,
            description: organization.description || '',
            isActive: organization.isActive ?? true, // Default to true if undefined
        },
        mode: 'onChange',
    })

    useEffect(() => {
        if (open && organization) {
            reset({
                name: organization.name,
                description: organization.description || '',
                isActive: organization.isActive ?? true, // Default to true if undefined
            })
        }
    }, [open, organization, reset])

    const onSubmit = (data: OrganizationFormData) => {
        setIsSubmitting(true)
        // isActive is now properly registered via Controller, so it will be in data
        updateOrganization({id: organization.id, input: data})
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[500px]">
                <DialogHeader>
                    <DialogTitle>Edit Organization</DialogTitle>
                    <DialogDescription>Update organization details</DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit as any)} className="space-y-4">
                    <div className="space-y-2">
                        <Label htmlFor="name">Name *</Label>
                        <Input
                            id="name"
                            {...register('name')}
                            placeholder="Organization name"
                            disabled={isSubmitting}
                        />
                        {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="description">Description</Label>
                        <Textarea
                            id="description"
                            {...register('description')}
                            placeholder="Organization description"
                            disabled={isSubmitting}
                            rows={3}
                        />
                    </div>
                    <div className="flex items-center justify-between">
                        <Label htmlFor="isActive">Active</Label>
                        <Controller
                            name="isActive"
                            control={control}
                            render={({field}) => (
                                <Switch
                                    id="isActive"
                                    checked={field.value}
                                    onCheckedChange={field.onChange}
                                    disabled={isSubmitting}
                                />
                            )}
                        />
                    </div>
                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
                            Cancel
                        </Button>
                        <Button type="submit" disabled={isSubmitting}>
                            {isSubmitting ? 'Updating...' : 'Update Organization'}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    )
}


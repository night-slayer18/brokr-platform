import {useState} from 'react'
import {useForm} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Textarea} from '@/components/ui/textarea'
import {Switch} from '@/components/ui/switch'
import {toast} from 'sonner'
import {CREATE_ORGANIZATION_MUTATION} from '@/graphql/mutations'
import type {CreateOrganizationMutation} from '@/graphql/types'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {useQueryClient} from '@tanstack/react-query'
import {GET_ORGANIZATIONS} from '@/graphql/queries'

const organizationSchema = z.object({
    name: z.string().min(1, 'Name is required'),
    description: z.string().optional().or(z.literal('')),
    isActive: z.boolean(),
})

type OrganizationFormData = z.infer<typeof organizationSchema>

interface CreateOrganizationDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
}

export function CreateOrganizationDialog({open, onOpenChange}: CreateOrganizationDialogProps) {
    const [isSubmitting, setIsSubmitting] = useState(false)
    const queryClient = useQueryClient()
    const {mutate: createOrganization} = useGraphQLMutation<CreateOrganizationMutation, {input: OrganizationFormData}>(
        CREATE_ORGANIZATION_MUTATION,
        {
            onSuccess: () => {
                toast.success('Organization created successfully')
                queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATIONS]})
                onOpenChange(false)
                reset()
            },
            onError: (error) => {
                toast.error(error.message || 'Failed to create organization')
                setIsSubmitting(false)
            },
        }
    )

    const {
        register,
        handleSubmit,
        reset,
        formState: {errors},
        watch,
        setValue,
    } = useForm<OrganizationFormData>({
        resolver: zodResolver(organizationSchema),
        defaultValues: {
            name: '',
            description: '',
            isActive: true,
        },
        mode: 'onChange',
    })

    const isActive = watch('isActive')

    const onSubmit = (data: OrganizationFormData) => {
        setIsSubmitting(true)
        createOrganization({input: data})
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[500px]">
                <DialogHeader>
                    <DialogTitle>Create Organization</DialogTitle>
                    <DialogDescription>Create a new organization to manage users and resources</DialogDescription>
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
                        <Switch
                            id="isActive"
                            checked={isActive}
                            onCheckedChange={(checked) => setValue('isActive', checked)}
                            disabled={isSubmitting}
                        />
                    </div>
                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
                            Cancel
                        </Button>
                        <Button type="submit" disabled={isSubmitting}>
                            {isSubmitting ? 'Creating...' : 'Create Organization'}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    )
}


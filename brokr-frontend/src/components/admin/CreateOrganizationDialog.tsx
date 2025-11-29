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
import {Checkbox} from '@/components/ui/checkbox'
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
    selectedEnvironmentTypes: z.array(z.string()).optional(),
})

type OrganizationFormData = z.infer<typeof organizationSchema>

interface CreateOrganizationDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
}

const STANDARD_ENVIRONMENTS = [
    {id: 'PRODUCTION', name: 'Production', type: 'PROD'},
    {id: 'NON_PROD_MAJOR', name: 'Major', type: 'NON_PROD_MAJOR'},
    {id: 'NON_PROD_MINOR', name: 'Minor', type: 'NON_PROD_MINOR'},
    {id: 'NON_PROD_HOTFIX', name: 'Hotfix', type: 'NON_PROD_HOTFIX'},
] as const

export function CreateOrganizationDialog({open, onOpenChange}: CreateOrganizationDialogProps) {
    const [isSubmitting, setIsSubmitting] = useState(false)
    const queryClient = useQueryClient()
    const {mutate: createOrganization} = useGraphQLMutation<CreateOrganizationMutation, {input: any}>(
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
            selectedEnvironmentTypes: [],
        },
        mode: 'onChange',
    })

    const isActive = watch('isActive')
    const selectedEnvironmentTypes = watch('selectedEnvironmentTypes') || []

    const handleEnvironmentToggle = (typeId: string, checked: boolean) => {
        const currentTypes = selectedEnvironmentTypes
        if (checked) {
            setValue('selectedEnvironmentTypes', [...currentTypes, typeId])
        } else {
            setValue('selectedEnvironmentTypes', currentTypes.filter(id => id !== typeId))
        }
    }

    const onSubmit = (data: OrganizationFormData) => {
        setIsSubmitting(true)
        
        // Transform selected types to initialEnvironments
        const initialEnvironments = data.selectedEnvironmentTypes?.map(typeId => {
            const envDef = STANDARD_ENVIRONMENTS.find(e => e.id === typeId)
            return {
                name: envDef?.name || typeId,
                type: envDef?.type || 'NON_PROD_MINOR',
                isActive: true,
                description: `${envDef?.name} environment`
            }
        }) || []

        createOrganization({
            input: {
                name: data.name,
                description: data.description,
                isActive: data.isActive,
                initialEnvironments
            }
        })
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[500px]">
                <DialogHeader>
                    <DialogTitle>Create Organization</DialogTitle>
                    <DialogDescription>Create a new organization to manage users and resources</DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                    <div className="space-y-4">
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
                    </div>

                    <div className="space-y-2">
                        <Label>Initial Environments</Label>
                        <div className="border rounded-md p-4 space-y-2">
                            {STANDARD_ENVIRONMENTS.map((env) => (
                                <div key={env.id} className="flex items-center space-x-2">
                                    <Checkbox
                                        id={`env-${env.id}`}
                                        checked={selectedEnvironmentTypes.includes(env.id)}
                                        onCheckedChange={(checked) => handleEnvironmentToggle(env.id, checked as boolean)}
                                        disabled={isSubmitting}
                                    />
                                    <Label
                                        htmlFor={`env-${env.id}`}
                                        className="text-sm font-normal cursor-pointer flex-1"
                                    >
                                        {env.name}
                                    </Label>
                                </div>
                            ))}
                        </div>
                        <p className="text-xs text-muted-foreground">
                            Select the environments to create for this organization.
                        </p>
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


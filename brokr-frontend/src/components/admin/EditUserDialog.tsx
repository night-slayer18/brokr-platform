import {useEffect, useState} from 'react'
import {useForm, Controller} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Switch} from '@/components/ui/switch'
import {Checkbox} from '@/components/ui/checkbox'
import {toast} from 'sonner'
import {UPDATE_USER_MUTATION} from '@/graphql/mutations'
import type {UpdateUserMutation, GetOrganizationQuery, GetEnvironmentsByOrganizationQuery} from '@/graphql/types'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {useQueryClient} from '@tanstack/react-query'
import {GET_ORGANIZATION, GET_ORGANIZATIONS, GET_USERS, GET_USER, GET_ENVIRONMENTS_BY_ORGANIZATION} from '@/graphql/queries'
import {ROLE_LABELS} from '@/lib/constants'
import {useAuth} from '@/hooks/useAuth'

const userSchema = z.object({
    username: z.string().min(1, 'Username is required'),
    email: z.string().email('Invalid email address'),
    password: z.string().optional().or(z.literal('')),
    firstName: z.string().optional().or(z.literal('')),
    lastName: z.string().optional().or(z.literal('')),
    role: z.enum(['VIEWER', 'ADMIN', 'SERVER_ADMIN', 'SUPER_ADMIN']),
    organizationId: z.string().optional(),
    accessibleEnvironmentIds: z.array(z.string()).optional(),
    isActive: z.boolean(),
})

type UserFormData = z.infer<typeof userSchema>

interface EditUserDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    user: NonNullable<GetOrganizationQuery['organization']['users']>[0]
    organizationId?: string
}

export function EditUserDialog({open, onOpenChange, user, organizationId}: EditUserDialogProps) {
    const [isSubmitting, setIsSubmitting] = useState(false)
    const queryClient = useQueryClient()
    const {hasRole} = useAuth()
    const isAdmin = hasRole('ADMIN')
    const isSuperAdminOrServerAdmin = user.role === 'SUPER_ADMIN' || user.role === 'SERVER_ADMIN'
    const userOrgId = user.organizationId || organizationId
    
    // Fetch environments for the organization
    const {data: environmentsData} = useGraphQLQuery<GetEnvironmentsByOrganizationQuery, {organizationId: string}>(
        GET_ENVIRONMENTS_BY_ORGANIZATION,
        userOrgId ? {organizationId: userOrgId} : undefined,
        {
            enabled: !!userOrgId && open,
        }
    )
    
    const environments = environmentsData?.environments || []
    
    const {mutate: updateUser} = useGraphQLMutation<UpdateUserMutation, {id: string; input: UserFormData}>(
        UPDATE_USER_MUTATION,
        {
            onSuccess: () => {
                toast.success('User updated successfully')
                queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATION]})
                queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATIONS]})
                queryClient.invalidateQueries({queryKey: ['graphql', GET_USERS]})
                queryClient.invalidateQueries({queryKey: ['graphql', GET_USER]})
                setIsSubmitting(false)
                onOpenChange(false)
            },
            onError: (error) => {
                toast.error(error.message || 'Failed to update user')
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
        control,
    } = useForm<UserFormData>({
        resolver: zodResolver(userSchema),
        defaultValues: {
            username: user.username,
            email: user.email,
            password: '',
            firstName: user.firstName || '',
            lastName: user.lastName || '',
            role: (user.role as 'VIEWER' | 'ADMIN' | 'SERVER_ADMIN' | 'SUPER_ADMIN') || 'VIEWER',
            organizationId: user.organizationId || organizationId,
            accessibleEnvironmentIds: user.accessibleEnvironmentIds || [],
            isActive: user.isActive,
        },
        mode: 'onChange',
    })

    useEffect(() => {
        if (open && user) {
            setIsSubmitting(false) // Reset submitting state when dialog opens
            reset({
                username: user.username,
                email: user.email,
                password: '',
                firstName: user.firstName || '',
                lastName: user.lastName || '',
                role: user.role as 'VIEWER' | 'ADMIN' | 'SERVER_ADMIN' | 'SUPER_ADMIN',
                organizationId: user.organizationId || organizationId,
                accessibleEnvironmentIds: user.accessibleEnvironmentIds || [],
                isActive: user.isActive ?? true, // Default to true if undefined
            })
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, user?.id, organizationId, reset]) // Only reset when user ID changes, not when user object changes

    const role = watch('role')
    const accessibleEnvironmentIds = watch('accessibleEnvironmentIds') || []

    const handleEnvironmentToggle = (environmentId: string, checked: boolean) => {
        const currentIds = accessibleEnvironmentIds
        if (checked) {
            setValue('accessibleEnvironmentIds', [...currentIds, environmentId])
        } else {
            setValue('accessibleEnvironmentIds', currentIds.filter(id => id !== environmentId))
        }
    }

    const onSubmit = (data: UserFormData) => {
        setIsSubmitting(true)
        // Only include password if it's provided
        const input = {...data}
        if (!input.password || input.password.trim() === '') {
            delete input.password
        }
        // isActive is now properly registered via Controller, so it will be in data
        updateUser({id: user.id, input})
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[500px]">
                <DialogHeader>
                    <DialogTitle>Edit User</DialogTitle>
                    <DialogDescription>Update user details</DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label htmlFor="username">Username *</Label>
                            <Input
                                id="username"
                                {...register('username')}
                                placeholder="Username"
                                disabled={isSubmitting}
                            />
                            {errors.username && <p className="text-sm text-destructive">{errors.username.message}</p>}
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="email">Email *</Label>
                            <Input
                                id="email"
                                type="email"
                                {...register('email')}
                                placeholder="Email"
                                disabled={isSubmitting}
                            />
                            {errors.email && <p className="text-sm text-destructive">{errors.email.message}</p>}
                        </div>
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="password">Password (leave blank to keep current)</Label>
                        <Input
                            id="password"
                            type="password"
                            {...register('password')}
                            placeholder="New password"
                            disabled={isSubmitting}
                        />
                        {errors.password && <p className="text-sm text-destructive">{errors.password.message}</p>}
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label htmlFor="firstName">First Name</Label>
                            <Input
                                id="firstName"
                                {...register('firstName')}
                                placeholder="First Name"
                                disabled={isSubmitting}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="lastName">Last Name</Label>
                            <Input
                                id="lastName"
                                {...register('lastName')}
                                placeholder="Last Name"
                                disabled={isSubmitting}
                            />
                        </div>
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="role">Role *</Label>
                        <Select
                            value={role}
                            onValueChange={(value) => setValue('role', value as UserFormData['role'])}
                            disabled={isSubmitting || (isAdmin && isSuperAdminOrServerAdmin)}
                        >
                            <SelectTrigger>
                                <SelectValue placeholder="Select role"/>
                            </SelectTrigger>
                            <SelectContent>
                                {Object.entries(ROLE_LABELS)
                                    .filter(([key]) => {
                                        // ADMIN cannot assign SUPER_ADMIN or SERVER_ADMIN roles
                                        if (isAdmin && (key === 'SUPER_ADMIN' || key === 'SERVER_ADMIN')) {
                                            return false
                                        }
                                        return true
                                    })
                                    .map(([key, label]) => (
                                        <SelectItem key={key} value={key}>
                                            {label}
                                        </SelectItem>
                                    ))}
                            </SelectContent>
                        </Select>
                        {errors.role && <p className="text-sm text-destructive">{errors.role.message}</p>}
                        {isAdmin && isSuperAdminOrServerAdmin && (
                            <p className="text-sm text-muted-foreground">ADMIN cannot modify SUPER_ADMIN or SERVER_ADMIN users</p>
                        )}
                    </div>
                    {userOrgId && (
                        <div className="space-y-2">
                            <Label>Accessible Environments *</Label>
                            <div className="border rounded-md p-4 space-y-2 max-h-48 overflow-y-auto">
                                {environments.length === 0 ? (
                                    <p className="text-sm text-muted-foreground">No environments available for this organization</p>
                                ) : (
                                    environments.map((env) => (
                                        <div key={env.id} className="flex items-center space-x-2">
                                            <Checkbox
                                                id={`env-${env.id}`}
                                                checked={accessibleEnvironmentIds.includes(env.id)}
                                                onCheckedChange={(checked) => handleEnvironmentToggle(env.id, checked as boolean)}
                                                disabled={isSubmitting}
                                            />
                                            <Label
                                                htmlFor={`env-${env.id}`}
                                                className="text-sm font-normal cursor-pointer flex-1"
                                            >
                                                {env.name} ({env.type})
                                            </Label>
                                        </div>
                                    ))
                                )}
                            </div>
                            <p className="text-xs text-muted-foreground">
                                Select which environments this user can access. Users can only see clusters in environments they have access to.
                            </p>
                        </div>
                    )}
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
                            {isSubmitting ? 'Updating...' : 'Update User'}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    )
}


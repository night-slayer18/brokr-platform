import {useState} from 'react'
import {useForm} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Switch} from '@/components/ui/switch'
import {toast} from 'sonner'
import {CREATE_USER_MUTATION} from '@/graphql/mutations'
import type {CreateUserMutation} from '@/graphql/types'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {useQueryClient} from '@tanstack/react-query'
import {GET_ORGANIZATION, GET_ORGANIZATIONS, GET_USERS} from '@/graphql/queries'
import {ROLE_LABELS} from '@/lib/constants'

const userSchema = z.object({
    username: z.string().min(1, 'Username is required'),
    email: z.string().email('Invalid email address'),
    password: z.string().min(8, 'Password must be at least 8 characters'),
    firstName: z.string().optional().or(z.literal('')),
    lastName: z.string().optional().or(z.literal('')),
    role: z.enum(['VIEWER', 'ADMIN', 'SERVER_ADMIN', 'SUPER_ADMIN']),
    organizationId: z.string().min(1, 'Organization is required'),
    accessibleEnvironmentIds: z.array(z.string()).optional(),
    isActive: z.boolean(),
})

type UserFormData = z.infer<typeof userSchema>

interface CreateUserDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    organizationId: string
}

export function CreateUserDialog({open, onOpenChange, organizationId}: CreateUserDialogProps) {
    const [isSubmitting, setIsSubmitting] = useState(false)
    const queryClient = useQueryClient()
    const {mutate: createUser} = useGraphQLMutation<CreateUserMutation, {input: UserFormData}>(
        CREATE_USER_MUTATION,
        {
            onSuccess: () => {
                toast.success('User created successfully')
                queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATION]})
                queryClient.invalidateQueries({queryKey: ['graphql', GET_ORGANIZATIONS]})
                queryClient.invalidateQueries({queryKey: ['graphql', GET_USERS]})
                onOpenChange(false)
                reset()
            },
            onError: (error) => {
                toast.error(error.message || 'Failed to create user')
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
    } = useForm<UserFormData>({
        resolver: zodResolver(userSchema),
        defaultValues: {
            username: '',
            email: '',
            password: '',
            firstName: '',
            lastName: '',
            role: 'VIEWER',
            organizationId: organizationId,
            accessibleEnvironmentIds: [],
            isActive: true,
        },
        mode: 'onChange',
    })

    const isActive = watch('isActive')
    const role = watch('role')

    const onSubmit = (data: UserFormData) => {
        setIsSubmitting(true)
        createUser({input: data})
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[500px]">
                <DialogHeader>
                    <DialogTitle>Create User</DialogTitle>
                    <DialogDescription>Create a new user for this organization</DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit as any)} className="space-y-4">
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
                        <Label htmlFor="password">Password *</Label>
                        <Input
                            id="password"
                            type="password"
                            {...register('password')}
                            placeholder="Password"
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
                            disabled={isSubmitting}
                        >
                            <SelectTrigger>
                                <SelectValue placeholder="Select role"/>
                            </SelectTrigger>
                            <SelectContent>
                                {Object.entries(ROLE_LABELS).map(([key, label]) => (
                                    <SelectItem key={key} value={key}>
                                        {label}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        {errors.role && <p className="text-sm text-destructive">{errors.role.message}</p>}
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
                            {isSubmitting ? 'Creating...' : 'Create User'}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    )
}


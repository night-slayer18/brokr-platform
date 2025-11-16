import {useState} from 'react'
import {useForm} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {toast} from 'sonner'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Textarea} from '@/components/ui/textarea'
import {Checkbox} from '@/components/ui/checkbox'
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog'
import {CREATE_API_KEY_MUTATION} from '@/graphql/mutations'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import type {CreateApiKeyMutation} from '@/graphql/types'
import {API_KEY_SCOPES} from '@/types'
import {Loader2, Key} from 'lucide-react'
import {ViewApiKeyDialog} from './ViewApiKeyDialog'
import type {ApiKeyGenerationResult} from '@/types'

const createApiKeySchema = z.object({
    name: z.string().min(1, 'Name is required').max(255, 'Name is too long'),
    description: z.string().max(1000, 'Description is too long').optional(),
    scopes: z.array(z.string()).min(1, 'At least one scope is required'),
    expiresAt: z.string().optional(),
})

type CreateApiKeyFormData = z.infer<typeof createApiKeySchema>

interface CreateApiKeyDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    onSuccess: () => void
}

export function CreateApiKeyDialog({open, onOpenChange, onSuccess}: CreateApiKeyDialogProps) {
    const [generatedKey, setGeneratedKey] = useState<ApiKeyGenerationResult | null>(null)
    const [showKeyDialog, setShowKeyDialog] = useState(false)
    
    const {mutate: createApiKey, isPending} = useGraphQLMutation<CreateApiKeyMutation>(
        CREATE_API_KEY_MUTATION
    )
    
    const {
        register,
        handleSubmit,
        watch,
        setValue,
        formState: {errors},
        reset,
    } = useForm<CreateApiKeyFormData>({
        resolver: zodResolver(createApiKeySchema),
        defaultValues: {
            scopes: [],
        },
    })
    
    const selectedScopes = watch('scopes')
    
    const handleScopeToggle = (scope: string) => {
        const currentScopes = selectedScopes || []
        if (currentScopes.includes(scope)) {
            setValue('scopes', currentScopes.filter((s) => s !== scope))
        } else {
            setValue('scopes', [...currentScopes, scope])
        }
    }
    
    const onSubmit = (data: CreateApiKeyFormData) => {
        createApiKey(
            {
                input: {
                    name: data.name,
                    description: data.description || null,
                    scopes: data.scopes,
                    expiresAt: data.expiresAt || null,
                },
            },
            {
                onSuccess: (result) => {
                    setGeneratedKey(result.createApiKey)
                    setShowKeyDialog(true)
                    reset()
                    onSuccess()
                },
                onError: (error: Error) => {
                    toast.error(error.message || 'Failed to create API key')
                },
            }
        )
    }
    
    const handleClose = () => {
        if (showKeyDialog) {
            toast.warning('Make sure you have saved your API key. You will not be able to see it again.')
        }
        setGeneratedKey(null)
        setShowKeyDialog(false)
        reset()
        onOpenChange(false)
    }
    
    return (
        <>
            <Dialog open={open && !showKeyDialog} onOpenChange={handleClose}>
                <DialogContent className="max-w-2xl max-h-[90vh] !grid !grid-rows-[auto_1fr_auto] p-0 gap-0">
                    <DialogHeader className="px-6 pt-6 pb-4">
                        <DialogTitle className="flex items-center gap-2">
                            <Key className="h-5 w-5"/>
                            Create API Key
                        </DialogTitle>
                        <DialogDescription>
                            Create a new API key for programmatic access to the Brokr platform
                        </DialogDescription>
                    </DialogHeader>
                    
                    <form id="create-api-key-form" onSubmit={handleSubmit(onSubmit)} className="space-y-6 overflow-y-auto px-6 min-h-0">
                        <div className="space-y-2">
                            <Label htmlFor="name">Name *</Label>
                            <Input
                                id="name"
                                {...register('name')}
                                placeholder="My API Key"
                                disabled={isPending}
                            />
                            {errors.name && (
                                <p className="text-sm text-destructive">{errors.name.message}</p>
                            )}
                        </div>
                        
                        <div className="space-y-2">
                            <Label htmlFor="description">Description</Label>
                            <Textarea
                                id="description"
                                {...register('description')}
                                placeholder="Optional description for this API key"
                                disabled={isPending}
                                rows={3}
                            />
                            {errors.description && (
                                <p className="text-sm text-destructive">{errors.description.message}</p>
                            )}
                        </div>
                        
                        <div className="space-y-2">
                            <Label>Scopes *</Label>
                            <p className="text-sm text-muted-foreground">
                                Select the permissions this API key should have
                            </p>
                            <div className="border rounded-lg p-4 max-h-64 overflow-y-auto space-y-2">
                                {API_KEY_SCOPES.map((scope) => (
                                    <div key={scope.value} className="flex items-start space-x-2">
                                        <Checkbox
                                            id={scope.value}
                                            checked={selectedScopes?.includes(scope.value) || false}
                                            onCheckedChange={() => handleScopeToggle(scope.value)}
                                            disabled={isPending}
                                        />
                                        <div className="flex-1">
                                            <Label
                                                htmlFor={scope.value}
                                                className="font-normal cursor-pointer"
                                            >
                                                {scope.label}
                                            </Label>
                                            <p className="text-xs text-muted-foreground">
                                                {scope.description}
                                            </p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                            {errors.scopes && (
                                <p className="text-sm text-destructive">{errors.scopes.message}</p>
                            )}
                        </div>
                        
                        <div className="space-y-2">
                            <Label htmlFor="expiresAt">Expiration Date (Optional)</Label>
                            <Input
                                id="expiresAt"
                                type="datetime-local"
                                {...register('expiresAt')}
                                disabled={isPending}
                            />
                            <p className="text-xs text-muted-foreground">
                                Leave empty for no expiration
                            </p>
                        </div>
                    </form>
                    
                    <div className="flex justify-end gap-2 px-6 pt-4 pb-6 border-t">
                        <Button type="button" variant="outline" onClick={handleClose} disabled={isPending}>
                            Cancel
                        </Button>
                        <Button type="submit" form="create-api-key-form" disabled={isPending}>
                            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                            {isPending ? 'Creating...' : 'Create API Key'}
                        </Button>
                    </div>
                </DialogContent>
            </Dialog>
            
            {generatedKey && (
                <ViewApiKeyDialog
                    open={showKeyDialog}
                    onOpenChange={(open) => {
                        setShowKeyDialog(open)
                        if (!open) {
                            handleClose()
                        }
                    }}
                    apiKey={generatedKey.apiKey}
                    fullKey={generatedKey.fullKey}
                    isNewKey={true}
                />
            )}
        </>
    )
}


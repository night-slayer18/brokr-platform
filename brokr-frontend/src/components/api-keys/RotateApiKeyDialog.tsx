import {useState} from 'react'
import {toast} from 'sonner'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog'
import {Alert, AlertDescription} from '@/components/ui/alert'
import {ROTATE_API_KEY_MUTATION} from '@/graphql/mutations'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import type {RotateApiKeyMutation} from '@/graphql/types'
import {Loader2, RotateCw, AlertTriangle} from 'lucide-react'
import {ViewApiKeyDialog} from './ViewApiKeyDialog'
import type {ApiKey, ApiKeyGenerationResult} from '@/types'

interface RotateApiKeyDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    apiKey: ApiKey
    onSuccess: () => void
}

export function RotateApiKeyDialog({
    open,
    onOpenChange,
    apiKey,
    onSuccess,
}: RotateApiKeyDialogProps) {
    const [gracePeriodDays, setGracePeriodDays] = useState(7)
    const [generatedKey, setGeneratedKey] = useState<ApiKeyGenerationResult | null>(null)
    const [showKeyDialog, setShowKeyDialog] = useState(false)
    
    const {mutate: rotateApiKey, isPending} = useGraphQLMutation<RotateApiKeyMutation>(
        ROTATE_API_KEY_MUTATION
    )
    
    const handleRotate = () => {
        rotateApiKey(
            {
                id: apiKey.id,
                gracePeriodDays: gracePeriodDays || undefined,
            },
            {
                onSuccess: (result) => {
                    setGeneratedKey(result.rotateApiKey)
                    setShowKeyDialog(true)
                    onSuccess()
                },
                onError: (error: Error) => {
                    toast.error(error.message || 'Failed to rotate API key')
                },
            }
        )
    }
    
    const handleClose = () => {
        if (showKeyDialog) {
            toast.warning('Make sure you have saved your new API key. The old key will stop working after the grace period.')
        }
        setGeneratedKey(null)
        setShowKeyDialog(false)
        setGracePeriodDays(7)
        onOpenChange(false)
    }
    
    return (
        <>
            <Dialog open={open && !showKeyDialog} onOpenChange={handleClose}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <RotateCw className="h-5 w-5"/>
                            Rotate API Key
                        </DialogTitle>
                        <DialogDescription>
                            Generate a new secret for this API key. The old key will continue to work during the grace period.
                        </DialogDescription>
                    </DialogHeader>
                    
                    <div className="space-y-4">
                        <Alert>
                            <AlertTriangle className="h-4 w-4"/>
                            <AlertDescription>
                                Rotating will generate a new secret. The old key will work for the grace period,
                                then it will stop working. Make sure to update all applications using this key.
                            </AlertDescription>
                        </Alert>
                        
                        <div className="space-y-2">
                            <Label htmlFor="gracePeriod">Grace Period (Days)</Label>
                            <Input
                                id="gracePeriod"
                                type="number"
                                min="0"
                                max="30"
                                value={gracePeriodDays}
                                onChange={(e) => setGracePeriodDays(parseInt(e.target.value) || 0)}
                                disabled={isPending}
                            />
                            <p className="text-xs text-muted-foreground">
                                How long the old key should continue working (0-30 days)
                            </p>
                        </div>
                        
                        <div className="flex justify-end gap-2">
                            <Button type="button" variant="outline" onClick={handleClose} disabled={isPending}>
                                Cancel
                            </Button>
                            <Button onClick={handleRotate} disabled={isPending}>
                                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                                {isPending ? 'Rotating...' : 'Rotate API Key'}
                            </Button>
                        </div>
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


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
import {Badge} from '@/components/ui/badge'
import {Alert, AlertDescription} from '@/components/ui/alert'
import {Copy, CheckCircle2, AlertTriangle, Key} from 'lucide-react'
import {format} from 'date-fns'
import type {ApiKey} from '@/types'

interface ViewApiKeyDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    apiKey: ApiKey
    fullKey?: string
    isNewKey?: boolean
}

export function ViewApiKeyDialog({
    open,
    onOpenChange,
    apiKey,
    fullKey,
    isNewKey = false,
}: ViewApiKeyDialogProps) {
    const [copied, setCopied] = useState(false)
    
    const handleCopy = () => {
        if (fullKey) {
            navigator.clipboard.writeText(fullKey)
            setCopied(true)
            toast.success('API key copied to clipboard')
            setTimeout(() => setCopied(false), 2000)
        }
    }
    
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-2xl">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <Key className="h-5 w-5"/>
                        {isNewKey ? 'API Key Created' : 'API Key Details'}
                    </DialogTitle>
                    <DialogDescription>
                        {isNewKey
                            ? 'Save this API key securely. You will not be able to see it again.'
                            : 'View API key details and usage information'}
                    </DialogDescription>
                </DialogHeader>
                
                <div className="space-y-6">
                    {isNewKey && fullKey && (
                        <Alert>
                            <AlertTriangle className="h-4 w-4"/>
                            <AlertDescription>
                                <strong>Important:</strong> Copy this API key now. You will not be able to see it again
                                after closing this dialog.
                            </AlertDescription>
                        </Alert>
                    )}
                    
                    {fullKey && (
                        <div className="space-y-2">
                            <Label>Full API Key {isNewKey && '(Save this securely)'}</Label>
                            <div className="flex items-center gap-2">
                                <Input
                                    value={fullKey}
                                    readOnly
                                    className="font-mono text-sm"
                                />
                                <Button
                                    variant="outline"
                                    size="icon"
                                    onClick={handleCopy}
                                    disabled={copied}
                                >
                                    {copied ? (
                                        <CheckCircle2 className="h-4 w-4 text-green-500"/>
                                    ) : (
                                        <Copy className="h-4 w-4"/>
                                    )}
                                </Button>
                            </div>
                        </div>
                    )}
                    
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label>Name</Label>
                            <p className="text-sm">{apiKey.name}</p>
                        </div>
                        <div className="space-y-2">
                            <Label>Key Prefix</Label>
                            <code className="text-xs bg-secondary px-2 py-1 rounded block">
                                {apiKey.keyPrefix}
                            </code>
                        </div>
                        {apiKey.description && (
                            <div className="space-y-2 col-span-2">
                                <Label>Description</Label>
                                <p className="text-sm">{apiKey.description}</p>
                            </div>
                        )}
                        <div className="space-y-2">
                            <Label>Status</Label>
                            <div>
                                {apiKey.isRevoked ? (
                                    <Badge variant="destructive">Revoked</Badge>
                                ) : apiKey.expiresAt && new Date(apiKey.expiresAt) < new Date() ? (
                                    <Badge variant="destructive">Expired</Badge>
                                ) : !apiKey.isActive ? (
                                    <Badge variant="secondary">Inactive</Badge>
                                ) : (
                                    <Badge variant="default">Active</Badge>
                                )}
                            </div>
                        </div>
                        <div className="space-y-2">
                            <Label>Created</Label>
                            <p className="text-sm">
                                {format(new Date(apiKey.createdAt), 'MMM d, yyyy HH:mm')}
                            </p>
                        </div>
                        {apiKey.expiresAt && (
                            <div className="space-y-2">
                                <Label>Expires</Label>
                                <p className="text-sm">
                                    {format(new Date(apiKey.expiresAt), 'MMM d, yyyy')}
                                </p>
                            </div>
                        )}
                        {apiKey.lastUsedAt && (
                            <div className="space-y-2">
                                <Label>Last Used</Label>
                                <p className="text-sm">
                                    {format(new Date(apiKey.lastUsedAt), 'MMM d, yyyy HH:mm')}
                                </p>
                            </div>
                        )}
                    </div>
                    
                    <div className="space-y-2">
                        <Label>Scopes</Label>
                        <div className="flex flex-wrap gap-2">
                            {apiKey.scopes.map((scope) => (
                                <Badge key={scope} variant="secondary">
                                    {scope}
                                </Badge>
                            ))}
                        </div>
                    </div>
                    
                    {apiKey.isRevoked && apiKey.revokedReason && (
                        <div className="space-y-2">
                            <Label>Revocation Reason</Label>
                            <p className="text-sm">{apiKey.revokedReason}</p>
                        </div>
                    )}
                </div>
            </DialogContent>
        </Dialog>
    )
}


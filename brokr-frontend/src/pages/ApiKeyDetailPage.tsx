import {useParams, useNavigate} from 'react-router-dom'
import {ArrowLeft, Key, Edit, RotateCw, Trash2, Copy} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Badge} from '@/components/ui/badge'
import {Skeleton} from '@/components/ui/skeleton'
import {GET_API_KEY} from '@/graphql/queries'
import type {GetApiKeyQuery} from '@/graphql/types'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {DELETE_API_KEY_MUTATION, REVOKE_API_KEY_MUTATION} from '@/graphql/mutations'
import {toast} from 'sonner'
import {format} from 'date-fns'
import {ApiKeyUsageChart} from '@/components/api-keys/ApiKeyUsageChart'
import {EditApiKeyDialog} from '@/components/api-keys/EditApiKeyDialog'
import {RotateApiKeyDialog} from '@/components/api-keys/RotateApiKeyDialog'
import {ViewApiKeyDialog} from '@/components/api-keys/ViewApiKeyDialog'
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
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {Label} from '@/components/ui/label'
import {useState} from 'react'
import type {ApiKey} from '@/types'

export default function ApiKeyDetailPage() {
    const {id} = useParams<{id: string}>()
    const navigate = useNavigate()
    const [editDialogOpen, setEditDialogOpen] = useState(false)
    const [rotateDialogOpen, setRotateDialogOpen] = useState(false)
    const [viewDialogOpen, setViewDialogOpen] = useState(false)
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
    const [revokeDialogOpen, setRevokeDialogOpen] = useState(false)
    
    const {data, isLoading, refetch} = useGraphQLQuery<GetApiKeyQuery>(
        GET_API_KEY,
        id ? {id} : undefined,
        {
            enabled: !!id,
        }
    )
    
    const {mutate: deleteApiKey, isPending: isDeleting} = useGraphQLMutation(DELETE_API_KEY_MUTATION)
    const {mutate: revokeApiKey, isPending: isRevoking} = useGraphQLMutation(REVOKE_API_KEY_MUTATION)
    
    const apiKey = data?.apiKey as ApiKey | undefined
    
    const handleDelete = () => {
        if (!apiKey || !id) return
        deleteApiKey({id}, {
            onSuccess: () => {
                toast.success('API key deleted successfully')
                navigate('/api-keys')
            },
            onError: (error: Error) => {
                toast.error(error.message || 'Failed to delete API key')
            },
        })
    }
    
    const handleRevoke = () => {
        if (!apiKey || !id) return
        revokeApiKey({id, reason: 'Revoked by user'}, {
            onSuccess: () => {
                toast.success('API key revoked successfully')
                refetch()
                setRevokeDialogOpen(false)
            },
            onError: (error: Error) => {
                toast.error(error.message || 'Failed to revoke API key')
            },
        })
    }
    
    const handleCopyKeyPrefix = (keyPrefix: string) => {
        navigator.clipboard.writeText(keyPrefix)
        toast.success('Key prefix copied to clipboard')
    }
    
    if (isLoading) {
        return (
            <div className="space-y-6">
                <div className="flex items-center gap-4">
                    <Skeleton className="h-10 w-10 rounded-md"/>
                    <Skeleton className="h-8 w-64"/>
                </div>
                <Card>
                    <CardHeader>
                        <Skeleton className="h-6 w-32"/>
                    </CardHeader>
                    <CardContent>
                        <Skeleton className="h-20 w-full"/>
                    </CardContent>
                </Card>
            </div>
        )
    }
    
    if (!apiKey) {
        return (
            <div className="space-y-6">
                <div className="flex items-center gap-4">
                    <Button variant="ghost" size="icon" onClick={() => navigate('/api-keys')}>
                        <ArrowLeft className="h-4 w-4"/>
                    </Button>
                    <div>
                        <h2 className="text-4xl font-bold tracking-tight">API Key Not Found</h2>
                    </div>
                </div>
                <Card>
                    <CardContent className="py-12 text-center">
                        <p className="text-muted-foreground">The API key you're looking for doesn't exist.</p>
                        <Button onClick={() => navigate('/api-keys')} className="mt-4">
                            Back to API Keys
                        </Button>
                    </CardContent>
                </Card>
            </div>
        )
    }
    
    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <Button variant="ghost" size="icon" onClick={() => navigate('/api-keys')}>
                        <ArrowLeft className="h-4 w-4"/>
                    </Button>
                    <div>
                        <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                            {apiKey.name}
                        </h2>
                        <p className="text-muted-foreground mt-1">
                            API Key Details and Usage Statistics
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    {!apiKey.isRevoked && (
                        <>
                            <Button
                                variant="outline"
                                onClick={() => setEditDialogOpen(true)}
                            >
                                <Edit className="h-4 w-4 mr-2"/>
                                Edit
                            </Button>
                            <Button
                                variant="outline"
                                onClick={() => setRotateDialogOpen(true)}
                            >
                                <RotateCw className="h-4 w-4 mr-2"/>
                                Rotate
                            </Button>
                            <Button
                                variant="outline"
                                onClick={() => setRevokeDialogOpen(true)}
                            >
                                <Trash2 className="h-4 w-4 mr-2"/>
                                Revoke
                            </Button>
                        </>
                    )}
                    <Button
                        variant="destructive"
                        onClick={() => setDeleteDialogOpen(true)}
                    >
                        <Trash2 className="h-4 w-4 mr-2"/>
                        Delete
                    </Button>
                </div>
            </div>
            
            <Tabs defaultValue="details" className="space-y-6">
                <TabsList>
                    <TabsTrigger value="details">Details</TabsTrigger>
                    <TabsTrigger value="usage">Usage Statistics</TabsTrigger>
                </TabsList>
                
                <TabsContent value="details" className="space-y-6">
                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                                <Key className="h-5 w-5 text-primary"/>
                                API Key Information
                            </CardTitle>
                            <CardDescription>
                                Basic information and configuration for this API key
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-6">
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div className="space-y-2">
                                    <Label>Name</Label>
                                    <p className="text-sm font-medium">{apiKey.name}</p>
                                </div>
                                
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
                                    <Label>Key Prefix</Label>
                                    <div className="flex items-center gap-2">
                                        <code className="text-xs bg-secondary px-2 py-1 rounded flex-1">
                                            {apiKey.keyPrefix}
                                        </code>
                                        <Button
                                            variant="ghost"
                                            size="icon"
                                            className="h-8 w-8"
                                            onClick={() => handleCopyKeyPrefix(apiKey.keyPrefix)}
                                        >
                                            <Copy className="h-4 w-4"/>
                                        </Button>
                                    </div>
                                </div>
                                
                                <div className="space-y-2">
                                    <Label>Created</Label>
                                    <p className="text-sm">
                                        {format(new Date(apiKey.createdAt), 'MMM d, yyyy HH:mm')}
                                    </p>
                                </div>
                                
                                {apiKey.description && (
                                    <div className="space-y-2 md:col-span-2">
                                        <Label>Description</Label>
                                        <p className="text-sm text-muted-foreground">{apiKey.description}</p>
                                    </div>
                                )}
                                
                                <div className="space-y-2">
                                    <Label>Last Used</Label>
                                    <p className="text-sm">
                                        {apiKey.lastUsedAt
                                            ? format(new Date(apiKey.lastUsedAt), 'MMM d, yyyy HH:mm')
                                            : 'Never'}
                                    </p>
                                </div>
                                
                                <div className="space-y-2">
                                    <Label>Expires</Label>
                                    <p className="text-sm">
                                        {apiKey.expiresAt
                                            ? format(new Date(apiKey.expiresAt), 'MMM d, yyyy HH:mm')
                                            : 'Never'}
                                    </p>
                                </div>
                                
                                {apiKey.isRevoked && apiKey.revokedAt && (
                                    <>
                                        <div className="space-y-2">
                                            <Label>Revoked At</Label>
                                            <p className="text-sm">
                                                {format(new Date(apiKey.revokedAt), 'MMM d, yyyy HH:mm')}
                                            </p>
                                        </div>
                                        {apiKey.revokedReason && (
                                            <div className="space-y-2">
                                                <Label>Revocation Reason</Label>
                                                <p className="text-sm text-muted-foreground">
                                                    {apiKey.revokedReason}
                                                </p>
                                            </div>
                                        )}
                                    </>
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
                        </CardContent>
                    </Card>
                </TabsContent>
                
                <TabsContent value="usage" className="space-y-6">
                    <ApiKeyUsageChart apiKeyId={apiKey.id} apiKeyName={apiKey.name} />
                </TabsContent>
            </Tabs>
            
            {apiKey && (
                <>
                    <EditApiKeyDialog
                        open={editDialogOpen}
                        onOpenChange={setEditDialogOpen}
                        apiKey={apiKey}
                        onSuccess={() => {
                            refetch()
                            setEditDialogOpen(false)
                            toast.success('API key updated successfully')
                        }}
                    />
                    
                    <RotateApiKeyDialog
                        open={rotateDialogOpen}
                        onOpenChange={setRotateDialogOpen}
                        apiKey={apiKey}
                        onSuccess={() => {
                            refetch()
                            setRotateDialogOpen(false)
                            toast.success('API key rotated successfully')
                        }}
                    />
                    
                    <ViewApiKeyDialog
                        open={viewDialogOpen}
                        onOpenChange={setViewDialogOpen}
                        apiKey={apiKey}
                    />
                </>
            )}
            
            <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Delete API Key?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Are you sure you want to permanently delete this API key? This action cannot be undone.
                            The key will be permanently removed from your account. Any applications using this key will immediately lose access.
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
            
            <AlertDialog open={revokeDialogOpen} onOpenChange={setRevokeDialogOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Revoke API Key?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Are you sure you want to revoke this API key? It will immediately stop working.
                            The key will be marked as revoked and will remain visible in your list for audit purposes, but you cannot restore or use it again.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isRevoking}>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                            onClick={handleRevoke}
                            disabled={isRevoking}
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                        >
                            {isRevoking ? 'Revoking...' : 'Revoke'}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    )
}


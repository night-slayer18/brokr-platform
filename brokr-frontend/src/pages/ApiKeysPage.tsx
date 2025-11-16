import {useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {ArrowLeft, Key, Plus, MoreVertical, Copy, RotateCw, Trash2, Edit, Eye} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table'
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {Badge} from '@/components/ui/badge'
import {GET_API_KEYS} from '@/graphql/queries'
import type {GetApiKeysQuery} from '@/graphql/types'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {
    DELETE_API_KEY_MUTATION,
    REVOKE_API_KEY_MUTATION,
} from '@/graphql/mutations'
import {toast} from 'sonner'
import {format} from 'date-fns'
import {CreateApiKeyDialog} from '@/components/api-keys/CreateApiKeyDialog'
import {EditApiKeyDialog} from '@/components/api-keys/EditApiKeyDialog'
import {RotateApiKeyDialog} from '@/components/api-keys/RotateApiKeyDialog'
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
import type {ApiKey} from '@/types'

export default function ApiKeysPage() {
    const navigate = useNavigate()
    const [createDialogOpen, setCreateDialogOpen] = useState(false)
    const [editDialogOpen, setEditDialogOpen] = useState(false)
    const [rotateDialogOpen, setRotateDialogOpen] = useState(false)
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
    const [revokeDialogOpen, setRevokeDialogOpen] = useState(false)
    const [selectedApiKey, setSelectedApiKey] = useState<ApiKey | null>(null)
    
    const {data, isLoading, refetch} = useGraphQLQuery<GetApiKeysQuery>(GET_API_KEYS)
    const {mutate: deleteApiKey, isPending: isDeleting} = useGraphQLMutation(DELETE_API_KEY_MUTATION)
    const {mutate: revokeApiKey, isPending: isRevoking} = useGraphQLMutation(REVOKE_API_KEY_MUTATION)
    
    const apiKeys = data?.apiKeys || []
    
    const handleDelete = () => {
        if (!selectedApiKey) return
        deleteApiKey({id: selectedApiKey.id}, {
            onSuccess: () => {
                toast.success('API key deleted successfully')
                refetch()
                setDeleteDialogOpen(false)
                setSelectedApiKey(null)
            },
            onError: (error: Error) => {
                toast.error(error.message || 'Failed to delete API key')
            },
        })
    }
    
    const handleRevoke = () => {
        if (!selectedApiKey) return
        revokeApiKey({id: selectedApiKey.id, reason: 'Revoked by user'}, {
            onSuccess: () => {
                toast.success('API key revoked successfully')
                refetch()
                setRevokeDialogOpen(false)
                setSelectedApiKey(null)
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
                    <div className="h-10 w-10 rounded-md bg-secondary animate-pulse"/>
                    <div className="h-8 w-64 bg-secondary animate-pulse rounded"/>
                </div>
                <Card>
                    <CardHeader>
                        <div className="h-6 w-32 bg-secondary animate-pulse rounded"/>
                    </CardHeader>
                    <CardContent>
                        <div className="h-20 w-full bg-secondary animate-pulse rounded"/>
                    </CardContent>
                </Card>
            </div>
        )
    }
    
    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                        <ArrowLeft className="h-4 w-4"/>
                    </Button>
                    <div>
                        <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                            API Keys
                        </h2>
                        <p className="text-muted-foreground mt-1">
                            Manage your API keys for programmatic access
                        </p>
                    </div>
                </div>
                <Button onClick={() => setCreateDialogOpen(true)}>
                    <Plus className="h-4 w-4 mr-2"/>
                    Create API Key
                </Button>
            </div>
            
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Key className="h-5 w-5 text-primary"/>
                        Your API Keys
                    </CardTitle>
                    <CardDescription>
                        API keys allow programmatic access to the Brokr platform. Keep them secure.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    {apiKeys.length === 0 ? (
                        <div className="text-center py-12">
                            <Key className="h-12 w-12 mx-auto text-muted-foreground mb-4"/>
                            <h3 className="text-lg font-semibold mb-2">No API keys yet</h3>
                            <p className="text-muted-foreground mb-4">
                                Create your first API key to get started with programmatic access
                            </p>
                            <Button onClick={() => setCreateDialogOpen(true)}>
                                <Plus className="h-4 w-4 mr-2"/>
                                Create API Key
                            </Button>
                        </div>
                    ) : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Name</TableHead>
                                    <TableHead>Key Prefix</TableHead>
                                    <TableHead>Scopes</TableHead>
                                    <TableHead>Status</TableHead>
                                    <TableHead>Last Used</TableHead>
                                    <TableHead>Expires</TableHead>
                                    <TableHead className="text-right">Actions</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {apiKeys.map((apiKey) => (
                                    <TableRow 
                                        key={apiKey.id}
                                        className="cursor-pointer hover:bg-muted/50"
                                        onClick={() => navigate(`/api-keys/${apiKey.id}`)}
                                    >
                                        <TableCell className="font-medium">
                                            <div>
                                                <div>{apiKey.name}</div>
                                                {apiKey.description && (
                                                    <div className="text-sm text-muted-foreground">
                                                        {apiKey.description}
                                                    </div>
                                                )}
                                            </div>
                                        </TableCell>
                                        <TableCell>
                                            <div className="flex items-center gap-2">
                                                <code className="text-xs bg-secondary px-2 py-1 rounded">
                                                    {apiKey.keyPrefix}
                                                </code>
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    className="h-6 w-6"
                                                    onClick={() => handleCopyKeyPrefix(apiKey.keyPrefix)}
                                                >
                                                    <Copy className="h-3 w-3"/>
                                                </Button>
                                            </div>
                                        </TableCell>
                                        <TableCell>
                                            <div className="flex flex-wrap gap-1">
                                                {apiKey.scopes.slice(0, 2).map((scope) => (
                                                    <Badge key={scope} variant="secondary" className="text-xs">
                                                        {scope}
                                                    </Badge>
                                                ))}
                                                {apiKey.scopes.length > 2 && (
                                                    <Badge variant="secondary" className="text-xs">
                                                        +{apiKey.scopes.length - 2}
                                                    </Badge>
                                                )}
                                            </div>
                                        </TableCell>
                                        <TableCell>
                                            {apiKey.isRevoked ? (
                                                <Badge variant="destructive">Revoked</Badge>
                                            ) : apiKey.expiresAt && new Date(apiKey.expiresAt) < new Date() ? (
                                                <Badge variant="destructive">Expired</Badge>
                                            ) : !apiKey.isActive ? (
                                                <Badge variant="secondary">Inactive</Badge>
                                            ) : (
                                                <Badge variant="default">Active</Badge>
                                            )}
                                        </TableCell>
                                        <TableCell>
                                            {apiKey.lastUsedAt
                                                ? format(new Date(apiKey.lastUsedAt), 'MMM d, yyyy HH:mm')
                                                : 'Never'}
                                        </TableCell>
                                        <TableCell>
                                            {apiKey.expiresAt
                                                ? format(new Date(apiKey.expiresAt), 'MMM d, yyyy')
                                                : 'Never'}
                                        </TableCell>
                                        <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                                            <DropdownMenu>
                                                <DropdownMenuTrigger asChild>
                                                    <Button variant="ghost" size="icon">
                                                        <MoreVertical className="h-4 w-4"/>
                                                    </Button>
                                                </DropdownMenuTrigger>
                                                <DropdownMenuContent align="end">
                                                    <DropdownMenuItem
                                                        onClick={() => {
                                                            navigate(`/api-keys/${apiKey.id}`)
                                                        }}
                                                    >
                                                        <Eye className="h-4 w-4 mr-2"/>
                                                        View Details
                                                    </DropdownMenuItem>
                                                    {!apiKey.isRevoked && (
                                                        <>
                                                            <DropdownMenuItem
                                                                onClick={() => {
                                                                    setSelectedApiKey(apiKey)
                                                                    setEditDialogOpen(true)
                                                                }}
                                                            >
                                                                <Edit className="h-4 w-4 mr-2"/>
                                                                Edit
                                                            </DropdownMenuItem>
                                                            <DropdownMenuItem
                                                                onClick={() => {
                                                                    setSelectedApiKey(apiKey)
                                                                    setRotateDialogOpen(true)
                                                                }}
                                                            >
                                                                <RotateCw className="h-4 w-4 mr-2"/>
                                                                Rotate
                                                            </DropdownMenuItem>
                                                            <DropdownMenuSeparator/>
                                                            <DropdownMenuItem
                                                                onClick={() => {
                                                                    setSelectedApiKey(apiKey)
                                                                    setRevokeDialogOpen(true)
                                                                }}
                                                            >
                                                                <Trash2 className="h-4 w-4 mr-2"/>
                                                                Revoke
                                                            </DropdownMenuItem>
                                                        </>
                                                    )}
                                                    <DropdownMenuItem
                                                        onClick={() => {
                                                            setSelectedApiKey(apiKey)
                                                            setDeleteDialogOpen(true)
                                                        }}
                                                    >
                                                        <Trash2 className="h-4 w-4 mr-2"/>
                                                        Delete
                                                    </DropdownMenuItem>
                                                </DropdownMenuContent>
                                            </DropdownMenu>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>
            
            <CreateApiKeyDialog
                open={createDialogOpen}
                onOpenChange={setCreateDialogOpen}
                onSuccess={() => {
                    refetch()
                    toast.success('API key created successfully')
                }}
            />
            
            {selectedApiKey && (
                <>
                    <EditApiKeyDialog
                        open={editDialogOpen}
                        onOpenChange={setEditDialogOpen}
                        apiKey={selectedApiKey}
                        onSuccess={() => {
                            refetch()
                            setEditDialogOpen(false)
                            setSelectedApiKey(null)
                            toast.success('API key updated successfully')
                        }}
                    />
                    
                    <RotateApiKeyDialog
                        open={rotateDialogOpen}
                        onOpenChange={setRotateDialogOpen}
                        apiKey={selectedApiKey}
                        onSuccess={() => {
                            refetch()
                            setRotateDialogOpen(false)
                            setSelectedApiKey(null)
                            toast.success('API key rotated successfully')
                        }}
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


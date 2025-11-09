import {useNavigate} from 'react-router-dom'
import {GET_ORGANIZATIONS} from '@/graphql/queries'
import type {GetOrganizationsQuery} from '@/graphql/types'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Badge} from '@/components/ui/badge'
import {Skeleton} from '@/components/ui/skeleton'
import {Building2, Plus, Users, Server, Eye} from 'lucide-react'
import {toast} from 'sonner'
import {useState} from 'react'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {CreateOrganizationDialog} from '@/components/admin/CreateOrganizationDialog'
import {EditOrganizationDialog} from '@/components/admin/EditOrganizationDialog'
import {DeleteOrganizationDialog} from '@/components/admin/DeleteOrganizationDialog'

export default function OrganizationsPage() {
    const navigate = useNavigate()
    const {data, isLoading, error} = useGraphQLQuery<GetOrganizationsQuery>(GET_ORGANIZATIONS)
    const [createDialogOpen, setCreateDialogOpen] = useState(false)
    const [editDialogOpen, setEditDialogOpen] = useState(false)
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
    const [selectedOrg, setSelectedOrg] = useState<GetOrganizationsQuery['organizations'][0] | null>(null)

    if (error) {
        toast.error('Failed to load organizations')
        return null
    }

    const handleView = (org: GetOrganizationsQuery['organizations'][0]) => {
        navigate(`/admin/organizations/${org.id}`)
    }

    const handleEdit = (org: GetOrganizationsQuery['organizations'][0]) => {
        setSelectedOrg(org)
        setEditDialogOpen(true)
    }

    const handleDelete = (org: GetOrganizationsQuery['organizations'][0]) => {
        setSelectedOrg(org)
        setDeleteDialogOpen(true)
    }

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight">Organizations</h1>
                    <p className="text-muted-foreground">Manage all organizations and their users</p>
                </div>
                <Button onClick={() => setCreateDialogOpen(true)}>
                    <Plus className="mr-2 h-4 w-4"/>
                    Create Organization
                </Button>
            </div>

            {isLoading ? (
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                    {[1, 2, 3].map((i) => (
                        <Card key={i}>
                            <CardHeader>
                                <Skeleton className="h-6 w-32"/>
                                <Skeleton className="h-4 w-48 mt-2"/>
                            </CardHeader>
                            <CardContent>
                                <Skeleton className="h-20 w-full"/>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            ) : (
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                    {data?.organizations?.map((org) => (
                        <Card key={org.id} className="hover:shadow-lg transition-shadow">
                            <CardHeader>
                                <div className="flex items-start justify-between">
                                    <div className="flex items-center gap-2">
                                        <Building2 className="h-5 w-5 text-primary"/>
                                        <CardTitle className="text-xl">{org.name}</CardTitle>
                                    </div>
                                    <Badge variant={org.isActive ? 'default' : 'secondary'}>
                                        {org.isActive ? 'Active' : 'Inactive'}
                                    </Badge>
                                </div>
                                {org.description && (
                                    <CardDescription className="mt-2">{org.description}</CardDescription>
                                )}
                            </CardHeader>
                            <CardContent>
                                <div className="space-y-3">
                                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                                        <Users className="h-4 w-4"/>
                                        <span>{org.users?.length || 0} users</span>
                                    </div>
                                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                                        <Server className="h-4 w-4"/>
                                        <span>{org.environments?.length || 0} environments</span>
                                    </div>
                                    <div className="flex gap-2 pt-2">
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => handleView(org)}
                                            className="flex-1"
                                        >
                                            <Eye className="mr-2 h-4 w-4"/>
                                            View
                                        </Button>
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => handleEdit(org)}
                                        >
                                            Edit
                                        </Button>
                                        <Button
                                            variant="destructive"
                                            size="sm"
                                            onClick={() => handleDelete(org)}
                                        >
                                            Delete
                                        </Button>
                                    </div>
                                </div>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            )}

            {data?.organizations?.length === 0 && !isLoading && (
                <Card>
                    <CardContent className="flex flex-col items-center justify-center py-12">
                        <Building2 className="h-12 w-12 text-muted-foreground mb-4"/>
                        <h3 className="text-lg font-semibold mb-2">No organizations found</h3>
                        <p className="text-muted-foreground mb-4">Get started by creating your first organization</p>
                        <Button onClick={() => setCreateDialogOpen(true)}>
                            <Plus className="mr-2 h-4 w-4"/>
                            Create Organization
                        </Button>
                    </CardContent>
                </Card>
            )}

            <CreateOrganizationDialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}/>
            {selectedOrg && (
                <>
                    <EditOrganizationDialog
                        open={editDialogOpen}
                        onOpenChange={setEditDialogOpen}
                        organization={selectedOrg}
                    />
                    <DeleteOrganizationDialog
                        open={deleteDialogOpen}
                        onOpenChange={setDeleteDialogOpen}
                        organization={selectedOrg}
                    />
                </>
            )}
        </div>
    )
}


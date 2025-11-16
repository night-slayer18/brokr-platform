import {useParams, useNavigate} from 'react-router-dom'
import {GET_ORGANIZATION} from '@/graphql/queries'
import type {GetOrganizationQuery} from '@/graphql/types'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Skeleton} from '@/components/ui/skeleton'
import {Switch} from '@/components/ui/switch'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {ArrowLeft, Building2, Users, Server, Edit, Trash2, Plus, Shield} from 'lucide-react'
import {useState, useEffect} from 'react'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {UPDATE_ORGANIZATION_MFA_POLICY_MUTATION} from '@/graphql/mutations'
import {ROLES} from '@/lib/constants'
import {UserHierarchyTree} from '@/components/admin/UserHierarchyTree'
import {UserDetailDrawer} from '@/components/admin/UserDetailDrawer'
import {EditOrganizationDialog} from '@/components/admin/EditOrganizationDialog'
import {DeleteOrganizationDialog} from '@/components/admin/DeleteOrganizationDialog'
import {CreateUserDialog} from '@/components/admin/CreateUserDialog'
import {useAuth} from '@/hooks/useAuth'
import {toast} from 'sonner'
import type {GetOrganizationQuery as OrgQuery} from '@/graphql/types'

export default function OrganizationDetailPage() {
    const {orgId} = useParams<{orgId: string}>()
    const navigate = useNavigate()
    const {canManageUsers, canManageOwnOrganization, canManageOrganizations} = useAuth()
    const {data, isLoading, error, refetch} = useGraphQLQuery<GetOrganizationQuery, {id: string}>(GET_ORGANIZATION, 
        orgId ? {id: orgId} : undefined,
        {
            enabled: !!orgId,
        }
    )
    const [selectedUser, setSelectedUser] = useState<NonNullable<OrgQuery['organization']['users']>[0] | null>(null)
    const [userDrawerOpen, setUserDrawerOpen] = useState(false)
    const [editDialogOpen, setEditDialogOpen] = useState(false)
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
    const [createUserDialogOpen, setCreateUserDialogOpen] = useState(false)
    const [mfaRequired, setMfaRequired] = useState(false)
    const [mfaGracePeriodDays, setMfaGracePeriodDays] = useState(7)
    
    const {mutate: updateMfaPolicy, isPending: updatingMfaPolicy} = useGraphQLMutation(UPDATE_ORGANIZATION_MFA_POLICY_MUTATION)
    
    const organization = data?.organization
    
    useEffect(() => {
        if (organization) {
            setMfaRequired(organization.mfaRequired || false)
            setMfaGracePeriodDays(organization.mfaGracePeriodDays || 7)
        }
    }, [organization])

    if (error) {
        return null
    }

    if (!orgId) {
        return null
    }

    // Update selectedUser when organization data changes to ensure we have fresh data
    useEffect(() => {
        if (selectedUser && organization?.users) {
            const updatedUser = organization.users.find(u => u.id === selectedUser.id)
            if (updatedUser) {
                setSelectedUser(updatedUser)
            }
        }
    }, [organization?.users, selectedUser?.id])

    const handleUserClick = (user: NonNullable<OrgQuery['organization']['users']>[0]) => {
        setSelectedUser(user)
        setUserDrawerOpen(true)
    }

    const handleEdit = () => {
        setEditDialogOpen(true)
    }

    const handleDelete = () => {
        setDeleteDialogOpen(true)
    }

    // Group users by role for hierarchy display
    const usersByRole = organization?.users?.reduce((acc: Record<string, NonNullable<typeof organization.users>>, user) => {
        const role = user.role
        if (!acc[role]) {
            acc[role] = []
        }
        acc[role].push(user)
        return acc
    }, {} as Record<string, NonNullable<typeof organization.users>>) || {}

    // Sort roles: ADMIN first, then VIEWER
    const sortedRoles = Object.keys(usersByRole).sort((a, b) => {
        if (a === ROLES.ADMIN) return -1
        if (b === ROLES.ADMIN) return 1
        return 0
    })

    return (
        <div className="space-y-6">
            <div className="flex items-center gap-4">
                <Button variant="ghost" size="icon" onClick={() => navigate('/admin/organizations')}>
                    <ArrowLeft className="h-4 w-4"/>
                </Button>
                <div className="flex-1">
                    {isLoading ? (
                        <Skeleton className="h-8 w-64 mb-2"/>
                    ) : (
                        <h1 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">{organization?.name}</h1>
                    )}
                    {isLoading ? (
                        <Skeleton className="h-4 w-96"/>
                    ) : (
                        <p className="text-muted-foreground">{organization?.description || 'No description'}</p>
                    )}
                </div>
                {!isLoading && organization && (
                    <div className="flex gap-2">
                        {canManageOwnOrganization(organization.id) && (
                            <Button variant="outline" onClick={handleEdit}>
                                <Edit className="mr-2 h-4 w-4"/>
                                Edit
                            </Button>
                        )}
                        {canManageOrganizations() && (
                            <Button variant="destructive" onClick={handleDelete}>
                                <Trash2 className="mr-2 h-4 w-4"/>
                                Delete
                            </Button>
                        )}
                    </div>
                )}
            </div>

            {isLoading ? (
                <div className="grid gap-4 md:grid-cols-3">
                    {[1, 2, 3].map((i) => (
                        <Card key={i}>
                            <CardHeader>
                                <Skeleton className="h-6 w-32"/>
                            </CardHeader>
                            <CardContent>
                                <Skeleton className="h-8 w-24"/>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            ) : (
                <div className="grid gap-4 md:grid-cols-3">
                    <Card>
                        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                            <CardTitle className="text-sm font-medium">Users</CardTitle>
                            <Users className="h-4 w-4 text-muted-foreground"/>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{organization?.users?.length || 0}</div>
                            <p className="text-xs text-muted-foreground">Total users in organization</p>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                            <CardTitle className="text-sm font-medium">Environments</CardTitle>
                            <Server className="h-4 w-4 text-muted-foreground"/>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{organization?.environments?.length || 0}</div>
                            <p className="text-xs text-muted-foreground">Total environments</p>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                            <CardTitle className="text-sm font-medium">Clusters</CardTitle>
                            <Building2 className="h-4 w-4 text-muted-foreground"/>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{organization?.clusters?.length || 0}</div>
                            <p className="text-xs text-muted-foreground">Total clusters</p>
                        </CardContent>
                    </Card>
                </div>
            )}

            {canManageOwnOrganization(organization?.id || '') && (
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <Shield className="h-5 w-5 text-primary"/>
                            MFA Policy
                        </CardTitle>
                        <CardDescription>Require Multi-Factor Authentication for all users in this organization</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-6">
                        <div className="flex items-center justify-between">
                            <div className="flex-1">
                                <Label htmlFor="mfa-required" className="text-base font-medium">
                                    Require MFA for all users
                                </Label>
                                <p className="text-sm text-muted-foreground mt-1">
                                    When enabled, all users in this organization must enable MFA to access the platform.
                                </p>
                            </div>
                            <Switch
                                id="mfa-required"
                                checked={mfaRequired}
                                onCheckedChange={setMfaRequired}
                                disabled={updatingMfaPolicy}
                            />
                        </div>
                        {mfaRequired && (
                            <div className="space-y-2">
                                <Label htmlFor="grace-period">Grace Period (days)</Label>
                                <Input
                                    id="grace-period"
                                    type="number"
                                    min="0"
                                    max="30"
                                    value={mfaGracePeriodDays}
                                    onChange={(e) => setMfaGracePeriodDays(parseInt(e.target.value) || 7)}
                                    disabled={updatingMfaPolicy}
                                />
                                <p className="text-xs text-muted-foreground">
                                    Number of days users have to enable MFA before being locked out. Default is 7 days.
                                </p>
                            </div>
                        )}
                        <Button
                            onClick={() => {
                                updateMfaPolicy(
                                    {
                                        organizationId: organization?.id || '',
                                        input: {
                                            mfaRequired,
                                            mfaGracePeriodDays: mfaRequired ? mfaGracePeriodDays : null,
                                        },
                                    },
                                    {
                                        onSuccess: () => {
                                            toast.success('MFA policy updated successfully')
                                            refetch()
                                        },
                                        onError: (error: Error) => {
                                            toast.error(error.message || 'Failed to update MFA policy')
                                        },
                                    }
                                )
                            }}
                            disabled={updatingMfaPolicy || !organization}
                        >
                            {updatingMfaPolicy ? 'Saving...' : 'Save MFA Policy'}
                        </Button>
                    </CardContent>
                </Card>
            )}

            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <div>
                            <CardTitle>User Hierarchy</CardTitle>
                            <CardDescription>Organization structure showing users by role</CardDescription>
                        </div>
                        {canManageUsers() && (
                            <Button onClick={() => setCreateUserDialogOpen(true)}>
                                <Plus className="mr-2 h-4 w-4"/>
                                Add User
                            </Button>
                        )}
                    </div>
                </CardHeader>
                <CardContent>
                    {isLoading ? (
                        <div className="space-y-4">
                            <Skeleton className="h-20 w-full"/>
                            <Skeleton className="h-20 w-full"/>
                        </div>
                    ) : (
                        <div className="space-y-6">
                            {sortedRoles.length === 0 ? (
                                <div className="text-center py-8 text-muted-foreground">
                                    <Users className="h-12 w-12 mx-auto mb-4 opacity-50"/>
                                    <p>No users in this organization</p>
                                    {canManageUsers() && (
                                        <Button onClick={() => setCreateUserDialogOpen(true)} className="mt-4">
                                            <Plus className="mr-2 h-4 w-4"/>
                                            Add First User
                                        </Button>
                                    )}
                                </div>
                            ) : (
                                <UserHierarchyTree
                                    usersByRole={usersByRole}
                                    sortedRoles={sortedRoles}
                                    onUserClick={handleUserClick}
                                />
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>

            {organization && (
                <>
                    <EditOrganizationDialog
                        open={editDialogOpen}
                        onOpenChange={setEditDialogOpen}
                        organization={organization}
                    />
                    <DeleteOrganizationDialog
                        open={deleteDialogOpen}
                        onOpenChange={setDeleteDialogOpen}
                        organization={organization}
                    />
                    <CreateUserDialog
                        open={createUserDialogOpen}
                        onOpenChange={setCreateUserDialogOpen}
                        organizationId={organization.id}
                    />
                </>
            )}

            {selectedUser && (
                <UserDetailDrawer
                    open={userDrawerOpen}
                    onOpenChange={setUserDrawerOpen}
                    user={selectedUser}
                    organizationId={organization?.id}
                />
            )}
        </div>
    )
}


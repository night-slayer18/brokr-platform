import {useNavigate} from 'react-router-dom'
import {GET_ORGANIZATIONS} from '@/graphql/queries'
import type {GetOrganizationsQuery} from '@/graphql/types'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Skeleton} from '@/components/ui/skeleton'
import {Building2, Users, Server, Plus, ArrowRight} from 'lucide-react'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {useState} from 'react'
import {CreateOrganizationDialog} from '@/components/admin/CreateOrganizationDialog'
import {useAuth} from '@/hooks/useAuth'

export default function AdminDashboardPage() {
    const navigate = useNavigate()
    const {canManageOrganizations} = useAuth()
    const {data, isLoading, error} = useGraphQLQuery<GetOrganizationsQuery>(GET_ORGANIZATIONS)
    const [createDialogOpen, setCreateDialogOpen] = useState(false)

    if (error) {
        return null
    }

    // Calculate statistics
    const totalOrganizations = data?.organizations?.length || 0
    const activeOrganizations = data?.organizations?.filter((org) => org.isActive).length || 0
    const totalUsers = data?.organizations?.reduce((sum, org) => sum + (org.users?.length || 0), 0) || 0
    const totalEnvironments = data?.organizations?.reduce((sum, org) => sum + (org.environments?.length || 0), 0) || 0

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">Admin Dashboard</h1>
                    <p className="text-muted-foreground">Overview of all organizations and resources</p>
                </div>
                {canManageOrganizations() && (
                    <Button onClick={() => setCreateDialogOpen(true)}>
                        <Plus className="mr-2 h-4 w-4"/>
                        Create Organization
                    </Button>
                )}
            </div>

            {isLoading ? (
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                    {[1, 2, 3, 4].map((i) => (
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
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                    <Card>
                        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                            <CardTitle className="text-sm font-medium">Total Organizations</CardTitle>
                            <Building2 className="h-4 w-4 text-muted-foreground"/>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{totalOrganizations}</div>
                            <p className="text-xs text-muted-foreground">{activeOrganizations} active</p>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                            <CardTitle className="text-sm font-medium">Total Users</CardTitle>
                            <Users className="h-4 w-4 text-muted-foreground"/>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{totalUsers}</div>
                            <p className="text-xs text-muted-foreground">Across all organizations</p>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                            <CardTitle className="text-sm font-medium">Total Environments</CardTitle>
                            <Server className="h-4 w-4 text-muted-foreground"/>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{totalEnvironments}</div>
                            <p className="text-xs text-muted-foreground">Across all organizations</p>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                            <CardTitle className="text-sm font-medium">Active Organizations</CardTitle>
                            <Building2 className="h-4 w-4 text-muted-foreground"/>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{activeOrganizations}</div>
                            <p className="text-xs text-muted-foreground">
                                {totalOrganizations > 0
                                    ? `${Math.round((activeOrganizations / totalOrganizations) * 100)}% of total`
                                    : 'No organizations'}
                            </p>
                        </CardContent>
                    </Card>
                </div>
            )}

            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <div>
                            <CardTitle>Organizations</CardTitle>
                            <CardDescription>Manage all organizations in the platform</CardDescription>
                        </div>
                        <Button variant="outline" onClick={() => navigate('/admin/organizations')}>
                            View All
                            <ArrowRight className="ml-2 h-4 w-4"/>
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    {isLoading ? (
                        <div className="space-y-2">
                            <Skeleton className="h-16 w-full"/>
                            <Skeleton className="h-16 w-full"/>
                        </div>
                    ) : data?.organizations && data.organizations.length > 0 ? (
                        <div className="space-y-2">
                            {data.organizations.slice(0, 5).map((org) => (
                                <div
                                    key={org.id}
                                    className="flex items-center justify-between p-3 border rounded-lg hover:bg-accent cursor-pointer"
                                    onClick={() => navigate(`/admin/organizations/${org.id}`)}
                                >
                                    <div className="flex items-center gap-3">
                                        <Building2 className="h-5 w-5 text-primary"/>
                                        <div>
                                            <p className="font-medium">{org.name}</p>
                                            <p className="text-sm text-muted-foreground">
                                                {org.users?.length || 0} users • {org.environments?.length || 0}{' '}
                                                environments
                                            </p>
                                        </div>
                                    </div>
                                    <Button variant="ghost" size="sm">
                                        View
                                    </Button>
                                </div>
                            ))}
                            {data.organizations.length > 5 && (
                                <div className="text-center pt-2">
                                    <Button variant="link" onClick={() => navigate('/admin/organizations')}>
                                        View {data.organizations.length - 5} more organizations
                                    </Button>
                                </div>
                            )}
                        </div>
                    ) : (
                        <div className="text-center py-8 text-muted-foreground">
                            <Building2 className="h-12 w-12 mx-auto mb-4 opacity-50"/>
                            <p>No organizations found</p>
                            {canManageOrganizations() && (
                                <Button onClick={() => setCreateDialogOpen(true)} className="mt-4">
                                    <Plus className="mr-2 h-4 w-4"/>
                                    Create First Organization
                                </Button>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>

            <CreateOrganizationDialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}/>
        </div>
    )
}


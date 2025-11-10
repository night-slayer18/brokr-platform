import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Avatar, AvatarFallback} from '@/components/ui/avatar'
import {Badge} from '@/components/ui/badge'
import {Skeleton} from '@/components/ui/skeleton'
import {Button} from '@/components/ui/button'
import {User, Mail, Building2, Shield, Server, ArrowLeft} from 'lucide-react'
import {GET_ME, GET_ORGANIZATION} from '@/graphql/queries'
import type {GetMeQuery, GetOrganizationQuery} from '@/graphql/types'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {useAuth} from '@/hooks/useAuth'
import {ROLE_LABELS} from '@/lib/constants'
import {useNavigate} from 'react-router-dom'

export default function ProfilePage() {
    const navigate = useNavigate()
    const {user: authUser} = useAuth()
    const {data, isLoading} = useGraphQLQuery<GetMeQuery>(GET_ME)
    
    const user = data?.me || authUser
    
    // Fetch organization details if user has an organization
    const {data: orgData} = useGraphQLQuery<GetOrganizationQuery, {id: string}>(
        GET_ORGANIZATION,
        user?.organizationId ? {id: user.organizationId} : undefined,
        {
            enabled: !!user?.organizationId,
        }
    )
    
    const organization = orgData?.organization

    const getUserInitials = () => {
        if (user?.firstName && user?.lastName) {
            return `${user.firstName[0]}${user.lastName[0]}`.toUpperCase()
        }
        return user?.username.substring(0, 2).toUpperCase() || 'U'
    }

    const getDisplayName = () => {
        if (user?.firstName && user?.lastName) {
            return `${user.firstName} ${user.lastName}`
        }
        return user?.username || 'User'
    }

    if (isLoading) {
        return (
            <div className="space-y-6">
                <div className="flex items-center gap-4">
                    <Skeleton className="h-10 w-10 rounded-md"/>
                    <Skeleton className="h-8 w-64"/>
                </div>
                <div className="grid gap-4 md:grid-cols-2">
                    <Card>
                        <CardHeader>
                            <Skeleton className="h-6 w-32"/>
                        </CardHeader>
                        <CardContent>
                            <Skeleton className="h-20 w-full"/>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader>
                            <Skeleton className="h-6 w-32"/>
                        </CardHeader>
                        <CardContent>
                            <Skeleton className="h-20 w-full"/>
                        </CardContent>
                    </Card>
                </div>
            </div>
        )
    }

    if (!user) {
        return (
            <div className="space-y-6">
                <div className="flex items-center gap-4">
                    <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                        <ArrowLeft className="h-4 w-4"/>
                    </Button>
                    <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                        Profile
                    </h2>
                </div>
                <Card>
                    <CardContent className="pt-6">
                        <p className="text-muted-foreground">User not found</p>
                    </CardContent>
                </Card>
            </div>
        )
    }

    return (
        <div className="space-y-6">
            <div className="flex items-center gap-4">
                <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                    <ArrowLeft className="h-4 w-4"/>
                </Button>
                <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                    Profile
                </h2>
            </div>

            <div className="grid gap-6 md:grid-cols-2">
                {/* User Information Card */}
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <User className="h-5 w-5 text-primary"/>
                            Personal Information
                        </CardTitle>
                        <CardDescription>Your account details and personal information</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-6">
                        <div className="flex items-center gap-4">
                            <Avatar className="h-20 w-20">
                                <AvatarFallback
                                    className="bg-linear-to-br from-primary to-primary/70 text-primary-foreground text-2xl font-semibold">
                                    {getUserInitials()}
                                </AvatarFallback>
                            </Avatar>
                            <div className="flex-1">
                                <h3 className="text-lg font-semibold">{getDisplayName()}</h3>
                                <p className="text-sm text-muted-foreground">{user.username}</p>
                            </div>
                        </div>

                        <div className="space-y-4 pt-4 border-t">
                            <div className="flex items-start gap-3">
                                <Mail className="h-4 w-4 text-muted-foreground mt-0.5"/>
                                <div className="flex-1 min-w-0">
                                    <p className="text-xs text-muted-foreground mb-1">Email</p>
                                    <p className="text-sm font-medium truncate">{user.email}</p>
                                </div>
                            </div>

                            {user.firstName && (
                                <div className="flex items-start gap-3">
                                    <User className="h-4 w-4 text-muted-foreground mt-0.5"/>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs text-muted-foreground mb-1">First Name</p>
                                        <p className="text-sm font-medium">{user.firstName}</p>
                                    </div>
                                </div>
                            )}

                            {user.lastName && (
                                <div className="flex items-start gap-3">
                                    <User className="h-4 w-4 text-muted-foreground mt-0.5"/>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs text-muted-foreground mb-1">Last Name</p>
                                        <p className="text-sm font-medium">{user.lastName}</p>
                                    </div>
                                </div>
                            )}
                        </div>
                    </CardContent>
                </Card>

                {/* Role & Organization Card */}
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <Shield className="h-5 w-5 text-primary"/>
                            Role & Organization
                        </CardTitle>
                        <CardDescription>Your role and organization information</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-6">
                        <div className="space-y-4">
                            <div className="flex items-start gap-3">
                                <Shield className="h-4 w-4 text-muted-foreground mt-0.5"/>
                                <div className="flex-1 min-w-0">
                                    <p className="text-xs text-muted-foreground mb-1">Role</p>
                                    <Badge variant="secondary" className="capitalize">
                                        {ROLE_LABELS[user.role as keyof typeof ROLE_LABELS] || user.role}
                                    </Badge>
                                </div>
                            </div>

                            {user.organizationId && (
                                <div className="flex items-start gap-3">
                                    <Building2 className="h-4 w-4 text-muted-foreground mt-0.5"/>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs text-muted-foreground mb-1">Organization</p>
                                        <p className="text-sm font-medium">{organization?.name || user.organizationId}</p>
                                        {organization?.name && (
                                            <p className="text-xs text-muted-foreground font-mono mt-0.5">{user.organizationId}</p>
                                        )}
                                    </div>
                                </div>
                            )}

                            <div className="flex items-start gap-3">
                                <Server className="h-4 w-4 text-muted-foreground mt-0.5"/>
                                <div className="flex-1 min-w-0">
                                    <p className="text-xs text-muted-foreground mb-1">Status</p>
                                    <Badge variant={user.isActive ? "default" : "secondary"}>
                                        {user.isActive ? "Active" : "Inactive"}
                                    </Badge>
                                </div>
                            </div>
                        </div>

                        {user.accessibleEnvironmentIds && user.accessibleEnvironmentIds.length > 0 && (
                            <div className="pt-4 border-t">
                                <p className="text-xs text-muted-foreground mb-2">Accessible Environments</p>
                                <div className="flex flex-wrap gap-2">
                                    {user.accessibleEnvironmentIds.map((envId) => (
                                        <Badge key={envId} variant="outline" className="font-mono text-xs">
                                            {envId.substring(0, 8)}...
                                        </Badge>
                                    ))}
                                </div>
                            </div>
                        )}
                    </CardContent>
                </Card>
            </div>
        </div>
    )
}


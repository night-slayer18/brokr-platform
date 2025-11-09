import {Avatar, AvatarFallback} from '@/components/ui/avatar'
import {Badge} from '@/components/ui/badge'
import {Card, CardContent} from '@/components/ui/card'
import {ROLE_LABELS} from '@/lib/constants'
import {Shield, Users} from 'lucide-react'
import type {GetOrganizationQuery} from '@/graphql/types'

interface UserHierarchyTreeProps {
    usersByRole: Record<string, NonNullable<GetOrganizationQuery['organization']['users']>>
    sortedRoles: string[]
    onUserClick: (user: NonNullable<GetOrganizationQuery['organization']['users']>[0]) => void
}

type UserType = NonNullable<GetOrganizationQuery['organization']['users']>[0]

export function UserHierarchyTree({usersByRole, sortedRoles, onUserClick}: UserHierarchyTreeProps) {
    const getUserInitials = (user: UserType) => {
        if (user.firstName && user.lastName) {
            return `${user.firstName[0]}${user.lastName[0]}`.toUpperCase()
        }
        return user.username.substring(0, 2).toUpperCase()
    }

    const getRoleIcon = (role: string) => {
        if (role === 'ADMIN') {
            return <Shield className="h-4 w-4"/>
        }
        return <Users className="h-4 w-4"/>
    }

    return (
        <div className="space-y-6">
            {sortedRoles.map((role) => {
                const users = usersByRole[role] || []
                return (
                    <div key={role} className="space-y-3">
                        <div className="flex items-center gap-2">
                            {getRoleIcon(role)}
                            <h3 className="text-lg font-semibold">{ROLE_LABELS[role as keyof typeof ROLE_LABELS] || role}</h3>
                            <Badge variant="secondary">{users.length}</Badge>
                        </div>
                        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
                            {users.map((user: UserType) => (
                                <Card
                                    key={user.id}
                                    className="cursor-pointer hover:shadow-md transition-shadow hover:border-primary/50"
                                    onClick={() => onUserClick(user)}
                                >
                                    <CardContent className="p-4">
                                        <div className="flex items-center gap-3">
                                            <Avatar>
                                                <AvatarFallback className="bg-gradient-to-br from-primary to-primary/70 text-primary-foreground">
                                                    {getUserInitials(user)}
                                                </AvatarFallback>
                                            </Avatar>
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2">
                                                    <p className="font-medium truncate">
                                                        {user.firstName && user.lastName
                                                            ? `${user.firstName} ${user.lastName}`
                                                            : user.username}
                                                    </p>
                                                    {!user.isActive && (
                                                        <Badge variant="secondary" className="text-xs">Inactive</Badge>
                                                    )}
                                                </div>
                                                <p className="text-sm text-muted-foreground truncate">{user.email}</p>
                                                <Badge variant="outline" className="mt-1 text-xs">
                                                    {ROLE_LABELS[user.role as keyof typeof ROLE_LABELS] || user.role}
                                                </Badge>
                                            </div>
                                        </div>
                                    </CardContent>
                                </Card>
                            ))}
                        </div>
                    </div>
                )
            })}
        </div>
    )
}


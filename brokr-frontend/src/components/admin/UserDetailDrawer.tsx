import {useState} from 'react'
import {Sheet, SheetContent, SheetDescription, SheetTitle} from '@/components/ui/sheet'
import {Avatar, AvatarFallback} from '@/components/ui/avatar'
import {Badge} from '@/components/ui/badge'
import {Button} from '@/components/ui/button'
import {Edit, Trash2, Mail, User, Shield, Building2, CheckCircle2, XCircle} from 'lucide-react'
import {ROLE_LABELS} from '@/lib/constants'
import type {GetOrganizationQuery} from '@/graphql/types'
import {EditUserDialog} from './EditUserDialog'
import {DeleteUserDialog} from './DeleteUserDialog'
import {useAuth} from '@/hooks/useAuth'

interface UserDetailDrawerProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    user: NonNullable<GetOrganizationQuery['organization']['users']>[0]
    organizationId?: string
}

export function UserDetailDrawer({open, onOpenChange, user, organizationId}: UserDetailDrawerProps) {
    const [editDialogOpen, setEditDialogOpen] = useState(false)
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
    const {canManageUsers, canManageUsersInOrganization, user: currentUser} = useAuth()
    const canManageThisUser = canManageUsers() && (canManageUsersInOrganization(user.organizationId || undefined) || currentUser?.id === user.id)

    const getUserInitials = () => {
        if (user.firstName && user.lastName) {
            return `${user.firstName[0]}${user.lastName[0]}`.toUpperCase()
        }
        return user.username.substring(0, 2).toUpperCase()
    }

    return (
        <>
            <Sheet open={open} onOpenChange={onOpenChange}>
                <SheetContent className="sm:max-w-[600px] w-full p-0 flex flex-col">
                    {/* Header Section */}
                    <div className="px-6 pt-6 pb-4 border-b bg-gradient-to-br from-primary/5 to-primary/10">
                        <div className="flex items-center gap-4 mb-4">
                            <Avatar className="h-20 w-20 flex-shrink-0 ring-4 ring-background shadow-lg">
                                <AvatarFallback className="bg-gradient-to-br from-primary to-primary/70 text-primary-foreground text-2xl font-bold">
                                    {getUserInitials()}
                                </AvatarFallback>
                            </Avatar>
                            <div className="flex-1 min-w-0">
                                <SheetTitle className="text-2xl font-bold mb-1">
                                    {user.firstName && user.lastName
                                        ? `${user.firstName} ${user.lastName}`
                                        : user.username}
                                </SheetTitle>
                                <SheetDescription className="text-base">{user.email}</SheetDescription>
                            </div>
                        </div>
                        {canManageThisUser && (
                            <div className="flex gap-2">
                                <Button 
                                    variant="outline" 
                                    onClick={() => setEditDialogOpen(true)} 
                                    className="flex-1 shadow-sm hover:shadow-md transition-shadow"
                                >
                                    <Edit className="mr-2 h-4 w-4"/>
                                    Edit User
                                </Button>
                                {currentUser?.id !== user.id && (
                                    <Button 
                                        variant="destructive" 
                                        onClick={() => setDeleteDialogOpen(true)} 
                                        className="flex-1 shadow-sm hover:shadow-md transition-shadow"
                                    >
                                        <Trash2 className="mr-2 h-4 w-4"/>
                                        Delete User
                                    </Button>
                                )}
                            </div>
                        )}
                    </div>

                    {/* Content Section */}
                    <div className="flex-1 overflow-y-auto px-6 py-6">
                        <div className="space-y-3">
                            {/* Username */}
                            <div className="bg-card border rounded-lg p-4 hover:shadow-md transition-shadow">
                                <div className="flex items-start gap-3">
                                    <div className="p-2 rounded-md bg-primary/10">
                                        <User className="h-5 w-5 text-primary"/>
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1.5">Username</p>
                                        <p className="text-base font-medium break-words">{user.username}</p>
                                    </div>
                                </div>
                            </div>

                            {/* Email */}
                            <div className="bg-card border rounded-lg p-4 hover:shadow-md transition-shadow">
                                <div className="flex items-start gap-3">
                                    <div className="p-2 rounded-md bg-primary/10">
                                        <Mail className="h-5 w-5 text-primary"/>
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1.5">Email</p>
                                        <p className="text-base font-medium break-words">{user.email}</p>
                                    </div>
                                </div>
                            </div>

                            {/* Role */}
                            <div className="bg-card border rounded-lg p-4 hover:shadow-md transition-shadow">
                                <div className="flex items-start gap-3">
                                    <div className="p-2 rounded-md bg-primary/10">
                                        <Shield className="h-5 w-5 text-primary"/>
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">Role</p>
                                        <Badge variant="secondary" className="text-sm px-3 py-1">
                                            {ROLE_LABELS[user.role as keyof typeof ROLE_LABELS] || user.role}
                                        </Badge>
                                    </div>
                                </div>
                            </div>

                            {/* Organization */}
                            <div className="bg-card border rounded-lg p-4 hover:shadow-md transition-shadow">
                                <div className="flex items-start gap-3">
                                    <div className="p-2 rounded-md bg-primary/10">
                                        <Building2 className="h-5 w-5 text-primary"/>
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1.5">Organization</p>
                                        <p className="text-base font-medium break-words">{organizationId || 'N/A'}</p>
                                    </div>
                                </div>
                            </div>

                            {/* Status */}
                            <div className="bg-card border rounded-lg p-4 hover:shadow-md transition-shadow">
                                <div className="flex items-start gap-3">
                                    <div className={`p-2 rounded-md ${user.isActive ? 'bg-green-500/10' : 'bg-red-500/10'}`}>
                                        {user.isActive ? (
                                            <CheckCircle2 className="h-5 w-5 text-green-600"/>
                                        ) : (
                                            <XCircle className="h-5 w-5 text-red-600"/>
                                        )}
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">Status</p>
                                        <Badge 
                                            variant={user.isActive ? 'default' : 'secondary'} 
                                            className={`text-sm px-3 py-1 ${user.isActive ? 'bg-green-500 text-white hover:bg-green-600' : 'bg-red-500 text-white hover:bg-red-600'}`}
                                        >
                                            {user.isActive ? 'Active' : 'Inactive'}
                                        </Badge>
                                    </div>
                                </div>
                            </div>

                            {/* Accessible Environments */}
                            {user.accessibleEnvironmentIds && user.accessibleEnvironmentIds.length > 0 && (
                                <div className="bg-card border rounded-lg p-4 hover:shadow-md transition-shadow">
                                    <div className="flex items-start gap-3">
                                        <div className="p-2 rounded-md bg-primary/10">
                                            <Building2 className="h-5 w-5 text-primary"/>
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-3">Accessible Environments</p>
                                            <div className="flex flex-wrap gap-2">
                                                {user.accessibleEnvironmentIds.map((envId: string) => (
                                                    <Badge 
                                                        key={envId} 
                                                        variant="outline" 
                                                        className="text-xs px-2.5 py-1 bg-primary/5 border-primary/20 hover:bg-primary/10 transition-colors"
                                                    >
                                                        {envId}
                                                    </Badge>
                                                ))}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </SheetContent>
            </Sheet>

            <EditUserDialog
                open={editDialogOpen}
                onOpenChange={setEditDialogOpen}
                user={user}
                organizationId={organizationId}
            />
            <DeleteUserDialog
                open={deleteDialogOpen}
                onOpenChange={setDeleteDialogOpen}
                user={user}
            />
        </>
    )
}



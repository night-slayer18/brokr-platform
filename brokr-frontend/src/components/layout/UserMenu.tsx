// src/components/layout/UserMenu.tsx
import {useAuthStore} from '@/store/authStore'
import {useNavigate} from 'react-router-dom'
import {Button} from '@/components/ui/button'
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {Avatar, AvatarFallback} from '@/components/ui/avatar'
import {LogOut, Settings, User} from 'lucide-react'

export function UserMenu() {
    const user = useAuthStore((state) => state.user)
    const logout = useAuthStore((state) => state.logout)
    const navigate = useNavigate()

    const handleLogout = () => {
        logout()
        navigate('/login')
    }

    const handleProfile = () => {
        navigate('/profile')
    }

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

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button variant="ghost" className="relative h-10 w-10 rounded-full">
                    <Avatar>
                        <AvatarFallback
                            className="bg-linear-to-br from-primary to-primary/70 text-primary-foreground font-semibold">
                            {getUserInitials()}
                        </AvatarFallback>
                    </Avatar>
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent className="w-64" align="end" forceMount>
                <DropdownMenuLabel className="p-4">
                    <div className="flex items-center gap-3">
                        <Avatar className="h-12 w-12">
                            <AvatarFallback
                                className="bg-linear-to-br from-primary to-primary/70 text-primary-foreground text-lg font-semibold">
                                {getUserInitials()}
                            </AvatarFallback>
                        </Avatar>
                        <div className="flex flex-col space-y-0.5 flex-1 min-w-0">
                            <p className="text-sm font-semibold leading-none truncate">{getDisplayName()}</p>
                            <p className="text-xs leading-none text-muted-foreground truncate">{user?.email}</p>
                            <p className="text-xs leading-none text-muted-foreground capitalize mt-1">{user?.role?.replace('_', ' ')}</p>
                        </div>
                    </div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator/>
                <DropdownMenuItem onClick={handleProfile} className="cursor-pointer">
                    <User className="mr-2 h-4 w-4"/>
                    <span>Profile</span>
                </DropdownMenuItem>
                <DropdownMenuItem disabled className="cursor-not-allowed opacity-50">
                    <Settings className="mr-2 h-4 w-4"/>
                    <span>Settings</span>
                    <span className="ml-auto text-xs text-muted-foreground">Coming soon</span>
                </DropdownMenuItem>
                <DropdownMenuSeparator/>
                <DropdownMenuItem onClick={handleLogout} className="cursor-pointer text-foreground hover:text-foreground focus:text-foreground">
                    <LogOut className="mr-2 h-4 w-4"/>
                    <span>Log out</span>
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    )
}


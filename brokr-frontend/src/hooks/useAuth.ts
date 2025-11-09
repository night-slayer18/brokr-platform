import {useAuthStore} from '@/store/authStore'

export function useAuth() {
    const user = useAuthStore((state) => state.user)
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
    const login = useAuthStore((state) => state.login)
    const logout = useAuthStore((state) => state.logout)

    const hasRole = (role: string | string[]) => {
        if (!user) return false
        // Normalize role comparison (case-insensitive, trim whitespace)
        const userRole = user.role?.trim().toUpperCase()
        if (Array.isArray(role)) {
            return role.some(r => r.trim().toUpperCase() === userRole)
        }
        return role.trim().toUpperCase() === userRole
    }

    const canManageTopics = () => {
        return hasRole(['ADMIN', 'SUPER_ADMIN'])
    }

    const canManageUsers = () => {
        return hasRole(['SERVER_ADMIN', 'SUPER_ADMIN'])
    }

    const canManageClusters = () => {
        return hasRole(['ADMIN', 'SUPER_ADMIN'])
    }

    const canManageOrganizations = () => {
        return hasRole('SUPER_ADMIN')
    }

    const isSuperAdmin = () => {
        return hasRole('SUPER_ADMIN')
    }

    const canAccessEnvironment = (environmentId: string) => {
        if (!user) return false
        if (user.role === 'SUPER_ADMIN') return true
        return user.accessibleEnvironmentIds.includes(environmentId)
    }

    return {
        user,
        isAuthenticated,
        login,
        logout,
        hasRole,
        canManageTopics,
        canManageUsers,
        canManageClusters,
        canManageOrganizations,
        isSuperAdmin,
        canAccessEnvironment,
    }
}
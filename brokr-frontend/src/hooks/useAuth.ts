import {useAuthStore} from '@/store/authStore'

export function useAuth() {
    const user = useAuthStore((state) => state.user)
    const token = useAuthStore((state) => state.token)
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
    const login = useAuthStore((state) => state.login)
    const logout = useAuthStore((state) => state.logout)

    const hasRole = (role: string | string[]) => {
        if (!user) return false
        if (Array.isArray(role)) {
            return role.includes(user.role)
        }
        return user.role === role
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

    const canAccessEnvironment = (environmentId: string) => {
        if (!user) return false
        if (user.role === 'SUPER_ADMIN') return true
        return user.accessibleEnvironmentIds.includes(environmentId)
    }

    return {
        user,
        token,
        isAuthenticated,
        login,
        logout,
        hasRole,
        canManageTopics,
        canManageUsers,
        canManageClusters, // Add this
        canAccessEnvironment,
    }
}
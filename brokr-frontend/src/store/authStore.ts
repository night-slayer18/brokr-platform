import { queryClient } from '@/lib/query-client'
import {create} from 'zustand'
import {persist} from 'zustand/middleware'

export interface User {
    id: string
    username: string
    email: string
    firstName?: string
    lastName?: string
    role: string
    organizationId?: string
    accessibleEnvironmentIds: string[]
    isActive: boolean
    mfaEnabled?: boolean
    mfaType?: string | null
}

interface AuthState {
    user: User | null
    isAuthenticated: boolean
    login: (user: User) => void
    logout: () => void
    updateUser: (user: User) => void
}

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            user: null,
            isAuthenticated: false,
            // Token is now stored in HttpOnly cookie, not in localStorage (secure against XSS)
            login: (user) => {
                set({user, isAuthenticated: true})
            },
            logout: async () => {
                // Call backend to clear the HttpOnly cookie
                try {
                    await fetch('/auth/logout', {
                        method: 'POST',
                        credentials: 'same-origin',
                    })
                } catch (error) {
                    console.error('Failed to logout on server:', error)
                }
                // Clear all queries from cache
                queryClient.clear()
                set({user: null, isAuthenticated: false})
            },
            updateUser: (user) => {
                // Invalidate all queries to refetch with new user data
                queryClient.invalidateQueries()
                set({user})
            },
        }),
        {
            name: 'brokr-auth',
            // Only persist user data, not token (token is in HttpOnly cookie)
            partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
        }
    )
)
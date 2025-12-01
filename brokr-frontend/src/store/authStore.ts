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
    logout: () => Promise<void>
    updateUser: (user: User) => void
    validateSession: () => Promise<boolean>
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
            /**
             * Validates if the current session is still valid on the backend.
             * This prevents the split-brain issue where localStorage shows user as logged in
             * but the backend JWT cookie has expired.
             * 
             * Call this on app startup to detect expired sessions immediately.
             */
            validateSession: async () => {
                const state = useAuthStore.getState()
                
                // If not authenticated in frontend state, no need to validate
                if (!state.isAuthenticated || !state.user) {
                    return false
                }
                
                try {
                    // Ping a lightweight endpoint to check if session is valid
                    const response = await fetch('/auth/validate', {
                        method: 'GET',
                        credentials: 'same-origin',
                    })
                    
                    if (!response.ok) {
                        // Session invalid - clear state
                        console.warn('Session validation failed, clearing auth state')
                        set({user: null, isAuthenticated: false})
                        queryClient.clear()
                        return false
                    }
                    
                    return true
                } catch (error) {
                    // Network error or session invalid - clear state to be safe
                    console.error('Session validation error:', error)
                    set({user: null, isAuthenticated: false})
                    queryClient.clear()
                    return false
                }
            },
        }),
        {
            name: 'brokr-auth',
            // Only persist user data, not token (token is in HttpOnly cookie)
            partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
        }
    )
)
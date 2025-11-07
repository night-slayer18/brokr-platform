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
}

interface AuthState {
    user: User | null
    token: string | null
    isAuthenticated: boolean
    login: (token: string, user: User) => void
    logout: () => void
    updateUser: (user: User) => void
}

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            user: null,
            token: null,
            isAuthenticated: false,
            login: (token, user) => {
                localStorage.setItem('brokr_token', token)
                localStorage.setItem('brokr_user', JSON.stringify(user))
                set({user, token, isAuthenticated: true})
            },
            logout: () => {
                localStorage.removeItem('brokr_token')
                localStorage.removeItem('brokr_user')
                set({user: null, token: null, isAuthenticated: false})
            },
            updateUser: (user) => {
                localStorage.setItem('brokr_user', JSON.stringify(user))
                set({user})
            },
        }),
        {
            name: 'brokr-auth',
        }
    )
)
// src/components/layout/Header.tsx
import {Link} from 'react-router-dom'
import {UserMenu} from './UserMenu'

export function Header() {
    return (
        <header className="border-b bg-card">
            <div className="flex h-16 items-center justify-between px-6">
                <Link to="/dashboard" className="flex items-center gap-3">
                    <img src="/brokr-icon.svg" alt="Brokr Logo" className="h-8 w-8"/>
                    <h1 className="text-xl font-semibold bg-gradient-to-r from-primary to-primary/80 bg-clip-text text-transparent">Brokr Platform</h1>
                </Link>
                <div className="flex items-center gap-4">
                    <UserMenu />
                </div>
            </div>
        </header>
    )
}
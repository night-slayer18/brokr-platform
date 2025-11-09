// src/components/layout/Header.tsx
import {UserMenu} from './UserMenu'

export function Header() {
    return (
        <header className="border-b bg-card">
            <div className="flex h-16 items-center justify-between px-6">
                <div className="flex items-center gap-4">
                    <h1 className="text-xl font-semibold">Brokr Platform</h1>
                </div>
                <div className="flex items-center gap-4">
                    <UserMenu />
                </div>
            </div>
        </header>
    )
}
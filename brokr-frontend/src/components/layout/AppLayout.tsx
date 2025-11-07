// src/components/layout/AppLayout.tsx
import {Outlet} from 'react-router-dom'
import {Header} from './Header'
import {Sidebar} from './Sidebar'

export function AppLayout() {
    return (
        <div className="flex h-screen bg-background">
            <Sidebar/>
            <div className="flex flex-1 flex-col overflow-hidden">
                <Header/>
                <main className="flex-1 overflow-y-auto p-6 bg-background">
                    <Outlet/>
                </main>
            </div>
        </div>
    )
}
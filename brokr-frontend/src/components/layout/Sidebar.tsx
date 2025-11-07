import {Link, useLocation} from 'react-router-dom'
import {cn} from '@/lib/utils'
import {LayoutDashboard, Server,} from 'lucide-react'
import {ScrollArea} from '@/components/ui/scroll-area'

const navigation = [
    {
        name: 'Dashboard',
        href: '/dashboard',
        icon: LayoutDashboard,
    },
    {
        name: 'Clusters',
        href: '/clusters',
        icon: Server,
    },
]

interface SidebarLinkProps {
    href: string
    icon: React.ElementType
    children: React.ReactNode
}

function SidebarLink({href, icon: Icon, children}: SidebarLinkProps) {
    const location = useLocation()
    const isActive = location.pathname === href || location.pathname.startsWith(href + '/')

    return (
        <Link
            to={href}
            className={cn(
                'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                isActive
                    ? 'bg-gradient-to-r from-primary/10 to-primary/20 text-primary'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
            )}
        >
            <Icon className="h-4 w-4"/>
            {children}
        </Link>
    )
}

export function Sidebar() {
    return (
        <aside className="w-64 border-r bg-card">
            <div className="flex h-16 items-center border-b px-6">
                <div className="flex items-center gap-2">
                    <div className="h-8 w-8 rounded-lg bg-gradient-to-br from-primary to-primary/70"/>
                    <span className="text-lg font-bold conduktor-gradient-text">Brokr</span>
                </div>
            </div>
            <ScrollArea className="flex-1 p-4">
                <nav className="space-y-1">
                    {navigation.map((item) => (
                        <SidebarLink key={item.href} href={item.href} icon={item.icon}>
                            {item.name}
                        </SidebarLink>
                    ))}
                </nav>
            </ScrollArea>
        </aside>
    )
}
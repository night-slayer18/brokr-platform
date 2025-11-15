import { Link, Outlet, useParams, useLocation } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { ScrollArea } from '@/components/ui/scroll-area';
import { LayoutDashboard, Server, Users, FileText, Database, PlugZap, Zap, Code, RotateCw } from 'lucide-react';
import { GET_CLUSTER } from '@/graphql/queries';
import type { GetClusterQuery } from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import { Skeleton } from '@/components/ui/skeleton';
import {
    Breadcrumb,
    BreadcrumbItem,
    BreadcrumbLink,
    BreadcrumbList,
    BreadcrumbPage,
    BreadcrumbSeparator
} from "@/components/ui/breadcrumb";
import { UserMenu } from './UserMenu';
import React from 'react';

export function ClusterLayout() {
    const { clusterId } = useParams<{ clusterId: string }>();
    const location = useLocation();

    const { data, isLoading: loading, error } = useGraphQLQuery<GetClusterQuery, {id: string}>(GET_CLUSTER, 
        clusterId ? {id: clusterId} : undefined,
        {
            enabled: !!clusterId,
            disableErrorNotification: true, // We'll handle error display in the component
        }
    );

    const clusterName = data?.cluster?.name || '...';

    // Handle error state
    if (error) {
        return (
            <div className="flex h-screen bg-background items-center justify-center">
                <div className="text-center space-y-4 max-w-md">
                    <h2 className="text-2xl font-bold text-destructive">Error Loading Cluster</h2>
                    <p className="text-muted-foreground">{error.message || 'Failed to load cluster details'}</p>
                    <Link to="/clusters" className="inline-block text-primary hover:underline">Go back to clusters</Link>
                </div>
            </div>
        );
    }

    // Handle case where cluster is not found (data exists but cluster is null)
    if (!loading && data && !data.cluster) {
        return (
            <div className="flex h-screen bg-background items-center justify-center">
                <div className="text-center space-y-4 max-w-md">
                    <h2 className="text-2xl font-bold text-destructive">Cluster Not Found</h2>
                    <p className="text-muted-foreground">The cluster you're looking for doesn't exist or you don't have access to it.</p>
                    <Link to="/clusters" className="inline-block text-primary hover:underline">Go back to clusters</Link>
                </div>
            </div>
        );
    }

    const navigation = [
        { name: 'Overview', href: `/clusters/${clusterId}`, icon: LayoutDashboard },
        { name: 'Brokers', href: `/clusters/${clusterId}/brokers`, icon: Server },
        { name: 'Topics', href: `/clusters/${clusterId}/topics`, icon: FileText },
        { name: 'Consumer Groups', href: `/clusters/${clusterId}/consumer-groups`, icon: Users },
        { name: 'Replay Jobs', href: `/clusters/${clusterId}/replay`, icon: RotateCw },
        { name: 'Schema Registry', href: `/clusters/${clusterId}/schema-registry`, icon: Database },
        { name: 'Kafka Connect', href: `/clusters/${clusterId}/kafka-connect`, icon: PlugZap },
        { name: 'Kafka Streams', href: `/clusters/${clusterId}/kafka-streams`, icon: Zap },
        { name: 'ksqlDB', href: `/clusters/${clusterId}/ksqldb`, icon: Code },
    ];

    const pathSegments = location.pathname.split('/').filter(Boolean);
    const breadcrumbItems = pathSegments.map((segment, index) => {
        const href = '/' + pathSegments.slice(0, index + 1).join('/');
        const isLast = index === pathSegments.length - 1;
        let name = segment.charAt(0).toUpperCase() + segment.slice(1);

        if (segment === clusterId) {
            name = clusterName;
        }

        return (
            <React.Fragment key={href}>
                <BreadcrumbItem>
                    {isLast ? (
                        <BreadcrumbPage>{name}</BreadcrumbPage>
                    ) : (
                        <BreadcrumbLink asChild>
                            <Link to={href}>{name}</Link>
                        </BreadcrumbLink>
                    )}
                </BreadcrumbItem>
                {!isLast && <BreadcrumbSeparator />}
            </React.Fragment>
        );
    });


    return (
        <div className="flex h-screen bg-background">
            <aside className="w-64 border-r bg-card flex flex-col">
                <div className="flex h-16 items-center border-b px-6">
                    {loading ? (
                        <Skeleton className="h-6 w-32" />
                    ) : (
                        <Link to="/clusters" className="flex items-center gap-2 font-semibold">
                            <Server className="h-6 w-6 text-primary" />
                            <span className="text-lg">{clusterName}</span>
                        </Link>
                    )}
                </div>
                <ScrollArea className="flex-1 p-4">
                    <nav className="space-y-1">
                        {navigation.map((item) => (
                            <Link
                                key={item.name}
                                to={item.href}
                                className={cn(
                                    'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                                    location.pathname === item.href
                                        ? 'bg-primary/10 text-primary'
                                        : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                                )}
                            >
                                <item.icon className="h-4 w-4" />
                                {item.name}
                            </Link>
                        ))}
                    </nav>
                </ScrollArea>
            </aside>
            <div className="flex flex-1 flex-col overflow-hidden">
                <header className="border-b bg-card h-16 flex items-center justify-between px-6">
                    <Breadcrumb>
                        <BreadcrumbList>
                            {breadcrumbItems}
                        </BreadcrumbList>
                    </Breadcrumb>
                    {/* Profile icon in header for easy access on all cluster pages */}
                    <div className="ml-auto">
                        <UserMenu />
                    </div>
                </header>
                <main className="flex-1 overflow-y-auto p-6">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

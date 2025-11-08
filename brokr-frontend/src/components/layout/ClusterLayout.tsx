import { Link, Outlet, useParams, useLocation } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { ScrollArea } from '@/components/ui/scroll-area';
import { LayoutDashboard, Server, Users, FileText, Database, PlugZap, Zap } from 'lucide-react';
import { useQuery } from '@apollo/client/react';
import { GET_CLUSTER } from '@/graphql/queries';
import type { GetClusterQuery, GetClusterVariables } from '@/graphql/types';
import { Skeleton } from '@/components/ui/skeleton';
import {
    Breadcrumb,
    BreadcrumbItem,
    BreadcrumbLink,
    BreadcrumbList,
    BreadcrumbPage,
    BreadcrumbSeparator
} from "@/components/ui/breadcrumb";
import React from 'react';

export function ClusterLayout() {
    const { clusterId } = useParams<{ clusterId: string }>();
    const location = useLocation();

    const { data, loading } = useQuery<GetClusterQuery, GetClusterVariables>(GET_CLUSTER, {
        variables: { id: clusterId! },
        skip: !clusterId,
    });

    const clusterName = data?.cluster?.name || '...';

    const navigation = [
        { name: 'Overview', href: `/clusters/${clusterId}`, icon: LayoutDashboard },
        { name: 'Brokers', href: `/clusters/${clusterId}/brokers`, icon: Server },
        { name: 'Topics', href: `/clusters/${clusterId}/topics`, icon: FileText },
        { name: 'Consumer Groups', href: `/clusters/${clusterId}/consumer-groups`, icon: Users },
        { name: 'Schema Registry', href: `/clusters/${clusterId}/schema-registry`, icon: Database },
        { name: 'Kafka Connect', href: `/clusters/${clusterId}/kafka-connect`, icon: PlugZap },
        { name: 'Kafka Streams', href: `/clusters/${clusterId}/kafka-streams`, icon: Zap },
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
                <header className="border-b bg-card h-16 flex items-center px-6">
                    <Breadcrumb>
                        <BreadcrumbList>
                            {breadcrumbItems}
                        </BreadcrumbList>
                    </Breadcrumb>
                </header>
                <main className="flex-1 overflow-y-auto p-6">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

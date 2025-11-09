import { useQuery } from '@apollo/client/react';
import { Link, useParams } from 'react-router-dom';
import { GET_CLUSTER_OVERVIEW } from '@/graphql/queries';
import type { GetClusterOverviewQuery, GetClusterOverviewVariables } from '@/graphql/types';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { ArrowRight, FileText, Server, Users } from 'lucide-react';
import React from 'react';

function StatCard({ title, value, description, icon: Icon, to }: { title: string, value: string | number, description: string, icon: React.ElementType, to: string }) {
    return (
        <Card className="hover:shadow-lg transition-shadow">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{title}</CardTitle>
                <Icon className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
                <div className="text-2xl font-bold">{value}</div>
                <p className="text-xs text-muted-foreground">{description}</p>
                <Link to={to} className="text-sm font-medium text-primary hover:underline flex items-center mt-2">
                    View all <ArrowRight className="h-4 w-4 ml-1" />
                </Link>
            </CardContent>
        </Card>
    );
}

export default function ClusterOverviewPage() {
    const { clusterId } = useParams<{ clusterId: string }>();

    const { data, loading, error } = useQuery<GetClusterOverviewQuery, GetClusterOverviewVariables>(GET_CLUSTER_OVERVIEW, {
        variables: { id: clusterId! },
        skip: !clusterId,
    });

    if (loading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-1/2" />
                <div className="grid gap-4 md:grid-cols-3">
                    <Skeleton className="h-32" />
                    <Skeleton className="h-32" />
                    <Skeleton className="h-32" />
                </div>
            </div>
        );
    }

    if (error) {
        return <div className="text-destructive">Error loading cluster details: {error.message}</div>;
    }

    const cluster = data?.cluster;
    const topicsCount = cluster?.topics?.length || 0;
    const consumerGroupsCount = cluster?.consumerGroups?.length || 0;
    const brokersCount = cluster?.brokers?.length || 0;

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
                    {cluster?.name} Overview
                    <Badge variant={cluster?.isReachable ? 'default' : 'destructive'}>
                        {cluster?.isReachable ? 'Online' : 'Offline'}
                    </Badge>
                </h2>
                <p className="text-muted-foreground">High-level view of your Kafka cluster.</p>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <StatCard title="Brokers" value={brokersCount} description="Total broker nodes" icon={Server} to={`/clusters/${clusterId}/brokers`} />
                <StatCard title="Topics" value={topicsCount} description="Total topics" icon={FileText} to={`/clusters/${clusterId}/topics`} />
                <StatCard title="Consumer Groups" value={consumerGroupsCount} description="Total consumer groups" icon={Users} to={`/clusters/${clusterId}/consumer-groups`} />
            </div>
        </div>
    );
}

import { Link, useParams } from 'react-router-dom';
import { GET_CLUSTER_OVERVIEW } from '@/graphql/queries';
import type { GetClusterOverviewQuery } from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { ArrowRight, FileText, Server, Users } from 'lucide-react';
import React, { memo, useState } from 'react';
import { ClusterMetricsChart } from '@/components/metrics/ClusterMetricsChart';
import { TimeRangeSelector } from '@/components/metrics/TimeRangeSelector';

const StatCard = memo(function StatCard({ title, value, description, icon: Icon, to }: { title: string, value: string | number, description: string, icon: React.ElementType, to: string }) {
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
});

function ClusterOverviewPage() {
    const { clusterId } = useParams<{ clusterId: string }>();
    const [timeRange, setTimeRange] = useState(() => {
        const endTime = Date.now();
        const startTime = endTime - (24 * 60 * 60 * 1000); // Last 24 hours
        return { startTime, endTime };
    });

    const { data, isLoading: loading, error } = useGraphQLQuery<GetClusterOverviewQuery, {id: string}>(GET_CLUSTER_OVERVIEW, 
        clusterId ? {id: clusterId} : undefined,
        {
            enabled: !!clusterId,
        }
    );

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
    // Handle null values from backend - if null, treat as empty array
    const topicsCount = cluster?.topics ? cluster.topics.length : 0;
    const consumerGroupsCount = cluster?.consumerGroups ? cluster.consumerGroups.length : 0;
    const brokersCount = cluster?.brokers ? cluster.brokers.length : 0;

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent flex items-center gap-2">
                    {cluster?.name} Overview
                    <Badge variant={cluster?.isReachable ? 'default' : 'destructive'}>
                        {cluster?.isReachable ? 'Online' : 'Offline'}
                    </Badge>
                </h2>
                <p className="text-muted-foreground mt-2">High-level view of your Kafka cluster.</p>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <StatCard title="Brokers" value={brokersCount} description="Total broker nodes" icon={Server} to={`/clusters/${clusterId}/brokers`} />
                <StatCard title="Topics" value={topicsCount} description="Total topics" icon={FileText} to={`/clusters/${clusterId}/topics`} />
                <StatCard title="Consumer Groups" value={consumerGroupsCount} description="Total consumer groups" icon={Users} to={`/clusters/${clusterId}/consumer-groups`} />
            </div>

            {clusterId && (
                <div className="space-y-4">
                    <TimeRangeSelector 
                        onTimeRangeChange={setTimeRange}
                    />
                    <ClusterMetricsChart
                        clusterId={clusterId}
                        timeRange={timeRange}
                    />
                </div>
            )}
        </div>
    );
}

export default memo(ClusterOverviewPage);

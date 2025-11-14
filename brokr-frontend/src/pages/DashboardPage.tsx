// src/pages/DashboardPage.tsx
import {GET_CLUSTERS, GET_ME, GET_AUDIT_LOGS} from '@/graphql/queries'
import type {GetClustersQuery, GetMeQuery, GetAuditLogsQuery} from '@/graphql/types'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Skeleton} from '@/components/ui/skeleton'
import {Activity, FileText, Server, Users} from 'lucide-react'
import React, {useMemo} from "react";
import {useNavigate} from "react-router-dom";
import type {KafkaCluster} from '@/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {useAuth} from '@/hooks/useAuth';
import {format} from 'date-fns';
import {ScrollArea} from '@/components/ui/scroll-area';

interface StatCardProps {
    title: string
    value: string | number
    description: string
    icon: React.ComponentType<{ className?: string }>
    trend?: number
}

function StatCard({title, value, description, icon: Icon, trend}: StatCardProps) {
    return (
        <Card
            className={`bg-linear-to-br from-primary/20 to-primary/10 border-primary/30 border backdrop-blur-sm hover:shadow-lg transition-all duration-300`}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-foreground/80">{title}</CardTitle>
                <div className={`p-2 rounded-lg bg-background/50`}>
                    <Icon className={`h-5 w-5 text-primary`}/>
                </div>
            </CardHeader>
            <CardContent>
                <div className="text-3xl font-bold text-foreground">{value}</div>
                <p className="text-xs text-muted-foreground mt-1">{description}</p>
                {trend && (
                    <div className={`text-xs mt-2 font-medium ${trend >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                        {trend >= 0 ? '↑' : '↓'} {Math.abs(trend)}% from last month
                    </div>
                )}
            </CardContent>
        </Card>
    )
}

export default function DashboardPage() {
    const navigate = useNavigate();
    const {canManageUsers, isSuperAdmin} = useAuth();
    const {data: userData, isLoading: userLoading} = useGraphQLQuery<GetMeQuery>(GET_ME)
    
    // For SUPER_ADMIN, don't filter by organizationId (they can see all clusters)
    // For other roles, use their organizationId
    const organizationIdForQuery = isSuperAdmin() ? null : userData?.me?.organizationId;
    const shouldFetchClusters = isSuperAdmin() || !!userData?.me?.organizationId;
    
    const {data: clustersData, isLoading: clustersLoading} = useGraphQLQuery<GetClustersQuery, {organizationId?: string | null}>(GET_CLUSTERS, 
        shouldFetchClusters ? (organizationIdForQuery ? {organizationId: organizationIdForQuery} : {organizationId: null}) : undefined,
        {
            enabled: shouldFetchClusters && !!userData?.me,
        }
    )

    // Fetch recent audit logs - only for admin users
    // SUPER_ADMIN can see all audit logs (no organizationId filter)
    // Other admins see their organization's audit logs
    const canAccessAuditLogs = canManageUsers();
    const shouldFetchAuditLogs = canAccessAuditLogs && !!userData?.me;
    const auditLogOrganizationId = isSuperAdmin() ? null : userData?.me?.organizationId;
    
    const {data: auditLogsData, isLoading: auditLogsLoading} = useGraphQLQuery<GetAuditLogsQuery>(
        GET_AUDIT_LOGS,
        {
            filter: {
                organizationId: auditLogOrganizationId || undefined,
            },
            pagination: {
                page: 0,
                size: 10,
                sortBy: 'timestamp',
                sortDirection: 'DESC',
            },
        },
        {
            enabled: shouldFetchAuditLogs,
        }
    )

    const clusterCount = (clustersData?.clusters || []).length
    const activeClusterCount = (clustersData?.clusters || []).filter((c: KafkaCluster) => c.isReachable)?.length || 0

    // Aggregate topic and consumer group counts from all clusters
    const {totalTopics, totalConsumerGroups} = useMemo(() => {
        if (!clustersData?.clusters) return { totalTopics: 0, totalConsumerGroups: 0 };
        
        let topics = 0;
        let consumerGroups = 0;
        
        clustersData.clusters.forEach((cluster: KafkaCluster) => {
            if (cluster.topics) {
                topics += cluster.topics.length;
            }
            if (cluster.consumerGroups) {
                consumerGroups += cluster.consumerGroups.length;
            }
        });
        
        return { totalTopics: topics, totalConsumerGroups: consumerGroups };
    }, [clustersData?.clusters]);

    const recentActivities = auditLogsData?.auditLogs?.content || [];

    if (userLoading || clustersLoading) {
        return (
            <div className="space-y-6">
                <div>
                    <Skeleton className="h-8 w-64"/>
                    <Skeleton className="h-4 w-96 mt-2"/>
                </div>
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                    {[1, 2, 3, 4].map((i) => (
                        <Skeleton key={i} className="h-32"/>
                    ))}
                </div>
            </div>
        )
    }

    const isLoadingMetrics = clustersLoading || auditLogsLoading;

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                    Dashboard
                </h2>
                <p className="text-muted-foreground mt-2 text-lg">
                    Welcome back, <span
                    className="text-foreground font-medium">{userData?.me?.firstName || userData?.me?.username}</span>!
                </p>
            </div>

            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4">
                <StatCard
                    title="Total Clusters"
                    value={clusterCount}
                    description={`${activeClusterCount} active`}
                    icon={Server}
                />
                <StatCard
                    title="Topics"
                    value={isLoadingMetrics ? "-" : totalTopics}
                    description={isLoadingMetrics ? "Loading..." : `Across ${clusterCount} cluster${clusterCount !== 1 ? 's' : ''}`}
                    icon={FileText}
                />
                <StatCard
                    title="Consumer Groups"
                    value={isLoadingMetrics ? "-" : totalConsumerGroups}
                    description={isLoadingMetrics ? "Loading..." : `Across ${clusterCount} cluster${clusterCount !== 1 ? 's' : ''}`}
                    icon={Users}
                />
                <StatCard
                    title="System Health"
                    value={activeClusterCount === clusterCount ? "Healthy" : "Warning"}
                    description={`${activeClusterCount}/${clusterCount} clusters online`}
                    icon={Activity}
                />
            </div>

            <div className="grid gap-4 md:grid-cols-2">
                <Card>
                    <CardHeader>
                        <CardTitle>Recent Activity</CardTitle>
                        <CardDescription>Latest operations and events</CardDescription>
                    </CardHeader>
                    <CardContent>
                        {!canAccessAuditLogs ? (
                            <p className="text-sm text-muted-foreground">
                                Recent activity is only available to administrators.
                            </p>
                        ) : auditLogsLoading ? (
                            <div className="space-y-2">
                                {[1, 2, 3].map((i) => (
                                    <Skeleton key={i} className="h-12 w-full"/>
                                ))}
                            </div>
                        ) : recentActivities.length === 0 ? (
                            <p className="text-sm text-muted-foreground">No recent activity to display</p>
                        ) : (
                            <ScrollArea className="h-[400px] pr-4">
                                <div className="space-y-3">
                                    {recentActivities.map((activity) => (
                                        <div key={activity.id} className="flex items-start justify-between border-b pb-3 last:border-0 last:pb-0">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2">
                                                    <span className="text-sm font-medium text-foreground">
                                                        {activity.userEmail || 'System'}
                                                    </span>
                                                    <span className="text-xs text-muted-foreground">
                                                        {activity.actionType}
                                                    </span>
                                                </div>
                                                <p className="text-sm text-muted-foreground mt-1 truncate">
                                                    {activity.resourceType}: {activity.resourceName || 'N/A'}
                                                </p>
                                                <p className="text-xs text-muted-foreground mt-1">
                                                    {format(new Date(activity.timestamp), 'MMM d, yyyy HH:mm:ss')}
                                                </p>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </ScrollArea>
                        )}
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle>Quick Actions</CardTitle>
                        <CardDescription>Common tasks and shortcuts</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="space-y-2">
                            <button onClick={() => navigate('/clusters')}
                                    className="w-full text-left text-sm hover:bg-accent p-2 rounded-md">
                                Create new topic
                            </button>
                            <button onClick={() => navigate('/clusters/new')}
                                    className="w-full text-left text-sm hover:bg-accent p-2 rounded-md">
                                Connect to cluster
                            </button>
                            <button onClick={() => navigate('/clusters')}
                                    className="w-full text-left text-sm hover:bg-accent p-2 rounded-md">
                                View consumer groups
                            </button>
                        </div>
                    </CardContent>
                </Card>
            </div>
        </div>
    )
}
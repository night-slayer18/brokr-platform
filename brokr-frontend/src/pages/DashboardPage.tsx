// src/pages/DashboardPage.tsx
import {GET_CLUSTERS, GET_ME} from '@/graphql/queries'
import type {GetClustersQuery, GetMeQuery} from '@/graphql/types'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Skeleton} from '@/components/ui/skeleton'
import {Activity, FileText, Server, Users} from 'lucide-react'
import React from "react";
import {useNavigate} from "react-router-dom";
import type {KafkaCluster} from '@/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';

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
    const {data: userData, isLoading: userLoading} = useGraphQLQuery<GetMeQuery>(GET_ME)
    const {data: clustersData, isLoading: clustersLoading} = useGraphQLQuery<GetClustersQuery, {organizationId?: string}>(GET_CLUSTERS, 
        userData?.me?.organizationId ? {organizationId: userData.me.organizationId} : undefined,
        {
            enabled: !!userData?.me?.organizationId,
        }
    )

    const clusterCount = (clustersData?.clusters || []).length
    const activeClusterCount = (clustersData?.clusters || []).filter((c: KafkaCluster) => c.isReachable)?.length || 0

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
                    value="-"
                    description="Select a cluster to view"
                    icon={FileText}
                />
                <StatCard
                    title="Consumer Groups"
                    value="-"
                    description="Select a cluster to view"
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
                        <p className="text-sm text-muted-foreground">No recent activity to display</p>
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
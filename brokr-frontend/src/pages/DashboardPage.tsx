// src/pages/DashboardPage.tsx
import {useQuery} from '@apollo/client/react'
import {GET_CLUSTERS, GET_ME} from '@/graphql/queries'
import type {GetClustersQuery, GetMeQuery} from '@/graphql/types'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Skeleton} from '@/components/ui/skeleton'
import {Activity, FileText, Server, Users} from 'lucide-react'
import React from "react";

type ColorType = 'orange' | 'teal' | 'purple' | 'amber' | 'blue' | 'emerald' | 'violet'

interface StatCardProps {
    title: string
    value: string | number
    description: string
    icon: React.ComponentType<{ className?: string }>
    trend?: number
    color: ColorType
}

function StatCard({title, value, description, icon: Icon, trend, color}: StatCardProps) {
    const colorClasses: Record<ColorType, string> = {
        orange: 'from-orange-500/20 to-orange-600/20 border-orange-500/30',
        teal: 'from-teal-500/20 to-teal-600/20 border-teal-500/30',
        purple: 'from-purple-500/20 to-purple-600/20 border-purple-500/30',
        amber: 'from-amber-500/20 to-amber-600/20 border-amber-500/30',
        blue: 'from-blue-500/20 to-blue-600/20 border-blue-500/30',
        emerald: 'from-emerald-500/20 to-emerald-600/20 border-emerald-500/30',
        violet: 'from-violet-500/20 to-violet-600/20 border-violet-500/30',
    }

    const iconColorClasses: Record<ColorType, string> = {
        orange: 'text-orange-400',
        teal: 'text-teal-400',
        purple: 'text-purple-400',
        amber: 'text-amber-400',
        blue: 'text-blue-400',
        emerald: 'text-emerald-400',
        violet: 'text-violet-400',
    }

    return (
        <Card
            className={`bg-gradient-to-br ${colorClasses[color]} border backdrop-blur-sm hover:shadow-lg transition-all duration-300`}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-foreground/80">{title}</CardTitle>
                <div className={`p-2 rounded-lg bg-background/50`}>
                    <Icon className={`h-5 w-5 ${iconColorClasses[color]}`}/>
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
    const {data: userData, loading: userLoading} = useQuery<GetMeQuery>(GET_ME)
    const {data: clustersData, loading: clustersLoading} = useQuery<GetClustersQuery>(GET_CLUSTERS)

    const clusterCount = clustersData?.clusters?.length || 0
    const activeClusterCount = clustersData?.clusters?.filter((c) => c.isReachable)?.length || 0

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
                <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-orange-400 to-teal-400 bg-clip-text text-transparent">
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
                    color="orange"
                />
                <StatCard
                    title="Topics"
                    value="-"
                    description="Select a cluster to view"
                    icon={FileText}
                    color="teal"
                />
                <StatCard
                    title="Consumer Groups"
                    value="-"
                    description="Select a cluster to view"
                    icon={Users}
                    color="purple"
                />
                <StatCard
                    title="System Health"
                    value={activeClusterCount === clusterCount ? "Healthy" : "Warning"}
                    description={`${activeClusterCount}/${clusterCount} clusters online`}
                    icon={Activity}
                    color="amber"
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
                            <button className="w-full text-left text-sm hover:bg-accent p-2 rounded-md">
                                Create new topic
                            </button>
                            <button className="w-full text-left text-sm hover:bg-accent p-2 rounded-md">
                                Connect to cluster
                            </button>
                            <button className="w-full text-left text-sm hover:bg-accent p-2 rounded-md">
                                View consumer groups
                            </button>
                        </div>
                    </CardContent>
                </Card>
            </div>
        </div>
    )
}
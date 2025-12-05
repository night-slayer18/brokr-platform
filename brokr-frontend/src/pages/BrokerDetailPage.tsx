import { useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { GET_CLUSTER, GET_LATEST_BROKER_METRICS } from '@/graphql/queries';
import type { GetClusterQuery } from '@/graphql/types';
import { useGraphQLQuery } from '@/hooks/useGraphQLQuery';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select';
import { 
    ArrowLeft,
    RefreshCw, 
    Server, 
    Cpu, 
    HardDrive,
    Activity,
    Crown,
    CheckCircle2,
    XCircle
} from 'lucide-react';
import { BrokerMetricsChart } from '@/components/metrics/BrokerMetricsChart';
import type { BrokerNode, BrokerMetrics } from '@/types';

const TIME_RANGE_OPTIONS = [
    { value: '1h', label: 'Last 1 hour', ms: 60 * 60 * 1000 },
    { value: '6h', label: 'Last 6 hours', ms: 6 * 60 * 60 * 1000 },
    { value: '24h', label: 'Last 24 hours', ms: 24 * 60 * 60 * 1000 },
    { value: '7d', label: 'Last 7 days', ms: 7 * 24 * 60 * 60 * 1000 },
];

function formatBytes(bytes: number | null | undefined): string {
    if (bytes == null) return 'N/A';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let unitIndex = 0;
    let value = bytes;
    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex++;
    }
    return `${value.toFixed(1)} ${units[unitIndex]}`;
}

function formatBytesPerSecond(bytes: number | null | undefined): string {
    if (bytes == null) return 'N/A';
    return `${formatBytes(bytes)}/s`;
}

export default function BrokerDetailPage() {
    const { clusterId, brokerId } = useParams<{ clusterId: string; brokerId: string }>();
    const navigate = useNavigate();
    const [timeRangeValue, setTimeRangeValue] = useState('1h');

    const brokerIdNum = parseInt(brokerId || '0', 10);

    const timeRange = useMemo(() => {
        const option = TIME_RANGE_OPTIONS.find(o => o.value === timeRangeValue) || TIME_RANGE_OPTIONS[0];
        const endTime = Date.now();
        const startTime = endTime - option.ms;
        return { startTime, endTime };
    }, [timeRangeValue]);

    const { 
        data: clusterData, 
        isLoading: clusterLoading, 
        error: clusterError 
    } = useGraphQLQuery<GetClusterQuery, {id: string}>(
        GET_CLUSTER, 
        clusterId ? { id: clusterId } : undefined,
        { enabled: !!clusterId }
    );

    const { 
        data: metricsData, 
        isLoading: metricsLoading,
        isFetching: metricsFetching,
        refetch: refetchMetrics
    } = useGraphQLQuery(
        GET_LATEST_BROKER_METRICS, 
        clusterId ? { clusterId } : undefined,
        { enabled: !!clusterId }
    );

    if (clusterLoading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-1/2" />
                <Skeleton className="h-96" />
            </div>
        );
    }

    if (clusterError) {
        return (
            <div className="text-destructive flex items-center gap-2">
                <XCircle className="h-5 w-5" />
                Error loading cluster: {clusterError.message}
            </div>
        );
    }

    const brokers = clusterData?.cluster?.brokers || [];
    const broker = brokers.find((b: BrokerNode) => b.id === brokerIdNum);
    const latestMetrics: BrokerMetrics[] = metricsData?.latestBrokerMetrics || [];
    const metrics = latestMetrics.find(m => m.brokerId === brokerIdNum);

    if (!broker) {
        return (
            <div className="text-destructive flex items-center gap-2">
                <XCircle className="h-5 w-5" />
                Broker {brokerId} not found
            </div>
        );
    }

    const isHealthy = metrics?.isHealthy ?? true;
    const isController = metrics?.isController ?? false;
    const cpuPercent = metrics?.cpuUsagePercent ?? 0;
    const memoryPercent = metrics?.memoryMaxBytes 
        ? ((metrics.memoryUsedBytes ?? 0) / metrics.memoryMaxBytes) * 100 
        : 0;

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                <div className="flex items-center gap-4">
                    <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                        <ArrowLeft className="h-5 w-5" />
                    </Button>
                    <div>
                        <div className="flex items-center gap-3">
                            <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-primary to-primary/60 bg-clip-text text-transparent">
                                Broker {broker.id}
                            </h2>
                            {isController && (
                                <Badge variant="outline" className="text-yellow-500 border-yellow-500/50">
                                    <Crown className="h-3 w-3 mr-1" />
                                    Controller
                                </Badge>
                            )}
                            {isHealthy ? (
                                <Badge variant="outline" className="text-green-500 border-green-500/50">
                                    <CheckCircle2 className="h-3 w-3 mr-1" />
                                    Healthy
                                </Badge>
                            ) : (
                                <Badge variant="outline" className="text-destructive border-destructive/50">
                                    <XCircle className="h-3 w-3 mr-1" />
                                    Unhealthy
                                </Badge>
                            )}
                        </div>
                        <p className="text-muted-foreground mt-1 font-mono">
                            {broker.host}:{broker.port}
                            {broker.rack && <span className="ml-2 text-sm">(Rack: {broker.rack})</span>}
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-3">
                    <Select value={timeRangeValue} onValueChange={setTimeRangeValue}>
                        <SelectTrigger className="w-[160px]">
                            <SelectValue placeholder="Time range" />
                        </SelectTrigger>
                        <SelectContent>
                            {TIME_RANGE_OPTIONS.map(option => (
                                <SelectItem key={option.value} value={option.value}>
                                    {option.label}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                    <Button 
                        variant="outline" 
                        size="icon"
                        onClick={() => refetchMetrics()}
                        disabled={metricsLoading || metricsFetching}
                    >
                        <RefreshCw className={`h-4 w-4 ${metricsFetching ? 'animate-spin' : ''}`} />
                    </Button>
                </div>
            </div>

            {/* Metrics Summary Cards */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <Card>
                    <CardContent className="pt-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm text-muted-foreground">CPU Usage</p>
                                <p className="text-2xl font-bold">{cpuPercent.toFixed(1)}%</p>
                            </div>
                            <div className="p-3 rounded-xl bg-primary/10">
                                <Cpu className="h-5 w-5 text-primary" />
                            </div>
                        </div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="pt-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm text-muted-foreground">Memory</p>
                                <p className="text-2xl font-bold">{memoryPercent.toFixed(1)}%</p>
                                <p className="text-xs text-muted-foreground">
                                    {formatBytes(metrics?.memoryUsedBytes)} / {formatBytes(metrics?.memoryMaxBytes)}
                                </p>
                            </div>
                            <div className="p-3 rounded-xl bg-primary/10">
                                <HardDrive className="h-5 w-5 text-primary" />
                            </div>
                        </div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="pt-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm text-muted-foreground">Throughput In</p>
                                <p className="text-2xl font-bold text-green-500">
                                    {formatBytesPerSecond(metrics?.bytesInPerSecond)}
                                </p>
                            </div>
                            <div className="p-3 rounded-xl bg-green-500/10">
                                <Activity className="h-5 w-5 text-green-500" />
                            </div>
                        </div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="pt-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm text-muted-foreground">Throughput Out</p>
                                <p className="text-2xl font-bold text-blue-500">
                                    {formatBytesPerSecond(metrics?.bytesOutPerSecond)}
                                </p>
                            </div>
                            <div className="p-3 rounded-xl bg-blue-500/10">
                                <Activity className="h-5 w-5 text-blue-500" />
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Partition Stats */}
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Server className="h-5 w-5" />
                        Partition Distribution
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
                        <div>
                            <p className="text-sm text-muted-foreground">Leader Partitions</p>
                            <p className="text-3xl font-bold">{metrics?.leaderPartitionCount ?? 0}</p>
                        </div>
                        <div>
                            <p className="text-sm text-muted-foreground">Replica Partitions</p>
                            <p className="text-3xl font-bold">{metrics?.replicaPartitionCount ?? 0}</p>
                        </div>
                        <div>
                            <p className="text-sm text-muted-foreground">Under-replicated</p>
                            <p className={`text-3xl font-bold ${(metrics?.underReplicatedPartitions ?? 0) > 0 ? 'text-yellow-500' : ''}`}>
                                {metrics?.underReplicatedPartitions ?? 0}
                            </p>
                        </div>
                        <div>
                            <p className="text-sm text-muted-foreground">Offline</p>
                            <p className={`text-3xl font-bold ${(metrics?.offlinePartitions ?? 0) > 0 ? 'text-destructive' : ''}`}>
                                {metrics?.offlinePartitions ?? 0}
                            </p>
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Detailed Charts */}
            {clusterId && (
                <BrokerMetricsChart
                    clusterId={clusterId}
                    brokerId={brokerIdNum}
                    timeRange={timeRange}
                />
            )}
        </div>
    );
}

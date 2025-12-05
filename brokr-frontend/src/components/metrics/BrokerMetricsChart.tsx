import { useGraphQLQuery } from '@/hooks/useGraphQLQuery';
import { GET_BROKER_METRICS_BY_BROKER } from '@/graphql/queries';
import { 
    LineChart, 
    Line, 
    XAxis, 
    YAxis, 
    CartesianGrid, 
    Tooltip, 
    Legend, 
    ResponsiveContainer, 
    AreaChart, 
    Area 
} from 'recharts';
import { format } from 'date-fns';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import type { BrokerMetrics } from '@/types';

interface BrokerMetricsChartProps {
    clusterId: string;
    brokerId: number;
    timeRange: { startTime: number; endTime: number };
    limit?: number;
}

function formatBytes(bytes: number): string {
    if (bytes >= 1024 * 1024 * 1024) {
        return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    } else if (bytes >= 1024 * 1024) {
        return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
    } else if (bytes >= 1024) {
        return `${(bytes / 1024).toFixed(2)} KB`;
    }
    return `${bytes} B`;
}

export function BrokerMetricsChart({ clusterId, brokerId, timeRange, limit = 1000 }: BrokerMetricsChartProps) {
    const { data, isLoading, error } = useGraphQLQuery(GET_BROKER_METRICS_BY_BROKER, {
        clusterId,
        brokerId,
        timeRange,
        limit
    });

    if (isLoading) {
        return (
            <Card>
                <CardHeader>
                    <CardTitle>Broker {brokerId} Metrics</CardTitle>
                    <CardDescription>Loading metrics...</CardDescription>
                </CardHeader>
                <CardContent>
                    <Skeleton className="h-[400px] w-full" />
                </CardContent>
            </Card>
        );
    }

    if (error) {
        return (
            <Card>
                <CardHeader>
                    <CardTitle>Broker {brokerId} Metrics</CardTitle>
                    <CardDescription>Error loading metrics</CardDescription>
                </CardHeader>
                <CardContent>
                    <p className="text-destructive">Failed to load metrics: {error.message}</p>
                </CardContent>
            </Card>
        );
    }

    const metrics: BrokerMetrics[] = data?.brokerMetricsByBroker || [];
    
    if (metrics.length === 0) {
        return (
            <Card>
                <CardHeader>
                    <CardTitle>Broker {brokerId} Metrics</CardTitle>
                    <CardDescription>No metrics available</CardDescription>
                </CardHeader>
                <CardContent>
                    <p className="text-muted-foreground">Metrics will appear here once collection starts.</p>
                </CardContent>
            </Card>
        );
    }

    // Sort by timestamp ascending (oldest first) for left-to-right time display
    const sortedMetrics = [...metrics].sort((a, b) => 
        new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
    );

    const chartData = sortedMetrics.map((metric) => ({
        time: format(new Date(metric.timestamp), 'HH:mm:ss'),
        timestamp: metric.timestamp,
        cpu: metric.cpuUsagePercent || 0,
        memoryUsed: metric.memoryUsedBytes || 0,
        memoryMax: metric.memoryMaxBytes || 0,
        memoryPercent: metric.memoryMaxBytes 
            ? ((metric.memoryUsedBytes || 0) / metric.memoryMaxBytes) * 100 
            : 0,
        bytesIn: metric.bytesInPerSecond || 0,
        bytesOut: metric.bytesOutPerSecond || 0,
        messagesIn: metric.messagesInPerSecond || 0,
        leaderPartitions: metric.leaderPartitionCount || 0,
        replicaPartitions: metric.replicaPartitionCount || 0,
    }));

    const tooltipStyle = { 
        backgroundColor: 'hsl(var(--background))', 
        border: '1px solid hsl(var(--border))',
        borderRadius: '8px',
        padding: '12px'
    };

    return (
        <Card>
            <CardHeader>
                <CardTitle>Broker {brokerId} Metrics</CardTitle>
                <CardDescription>Performance metrics and resource utilization over time</CardDescription>
            </CardHeader>
            <CardContent>
                <Tabs defaultValue="resources" className="w-full">
                    <TabsList className="grid w-full grid-cols-3">
                        <TabsTrigger value="resources">Resources</TabsTrigger>
                        <TabsTrigger value="throughput">Throughput</TabsTrigger>
                        <TabsTrigger value="partitions">Partitions</TabsTrigger>
                    </TabsList>

                    {/* Resources Tab */}
                    <TabsContent value="resources" className="mt-4">
                        <Card>
                            <CardHeader>
                                <CardTitle>CPU & Memory Usage</CardTitle>
                                <CardDescription>Resource utilization percentage over time</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <ResponsiveContainer width="100%" height={400}>
                                    <AreaChart data={chartData}>
                                        <CartesianGrid strokeDasharray="3 3" />
                                        <XAxis 
                                            dataKey="time" 
                                            tick={{ fontSize: 12 }}
                                            interval="preserveStartEnd"
                                        />
                                        <YAxis 
                                            tick={{ fontSize: 12 }}
                                            domain={[0, 100]}
                                            label={{ value: 'Usage %', angle: -90, position: 'insideLeft' }}
                                        />
                                        <Tooltip 
                                            contentStyle={tooltipStyle}
                                            labelFormatter={(value) => `Time: ${value}`}
                                            formatter={(value: number, name: string) => [
                                                `${value.toFixed(1)}%`, 
                                                name === 'cpu' ? 'CPU' : 'Memory'
                                            ]}
                                        />
                                        <Legend />
                                        <Area 
                                            type="monotone" 
                                            dataKey="cpu" 
                                            stroke="#8b5cf6" 
                                            fill="#8b5cf6"
                                            fillOpacity={0.6}
                                            name="CPU %" 
                                        />
                                        <Area 
                                            type="monotone" 
                                            dataKey="memoryPercent" 
                                            stroke="#06b6d4" 
                                            fill="#06b6d4"
                                            fillOpacity={0.6}
                                            name="Memory %" 
                                        />
                                    </AreaChart>
                                </ResponsiveContainer>
                            </CardContent>
                        </Card>
                    </TabsContent>

                    {/* Throughput Tab */}
                    <TabsContent value="throughput" className="mt-4">
                        <Card>
                            <CardHeader>
                                <CardTitle>Data Throughput</CardTitle>
                                <CardDescription>Bytes in/out per second over time</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <ResponsiveContainer width="100%" height={400}>
                                    <LineChart data={chartData}>
                                        <CartesianGrid strokeDasharray="3 3" />
                                        <XAxis 
                                            dataKey="time" 
                                            tick={{ fontSize: 12 }}
                                            interval="preserveStartEnd"
                                        />
                                        <YAxis 
                                            tick={{ fontSize: 12 }}
                                            tickFormatter={(value) => formatBytes(value)}
                                        />
                                        <Tooltip 
                                            contentStyle={tooltipStyle}
                                            labelFormatter={(value) => `Time: ${value}`}
                                            formatter={(value: number, name: string) => [
                                                `${formatBytes(value)}/s`, 
                                                name === 'bytesIn' ? 'Bytes In' : 'Bytes Out'
                                            ]}
                                        />
                                        <Legend />
                                        <Line 
                                            type="monotone" 
                                            dataKey="bytesIn" 
                                            stroke="#22c55e" 
                                            name="Bytes In" 
                                            strokeWidth={2}
                                            dot={false}
                                        />
                                        <Line 
                                            type="monotone" 
                                            dataKey="bytesOut" 
                                            stroke="#3b82f6" 
                                            name="Bytes Out" 
                                            strokeWidth={2}
                                            dot={false}
                                        />
                                    </LineChart>
                                </ResponsiveContainer>
                            </CardContent>
                        </Card>
                    </TabsContent>

                    {/* Partitions Tab */}
                    <TabsContent value="partitions" className="mt-4">
                        <Card>
                            <CardHeader>
                                <CardTitle>Partition Distribution</CardTitle>
                                <CardDescription>Leader and replica partition counts over time</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <ResponsiveContainer width="100%" height={400}>
                                    <AreaChart data={chartData}>
                                        <CartesianGrid strokeDasharray="3 3" />
                                        <XAxis 
                                            dataKey="time" 
                                            tick={{ fontSize: 12 }}
                                            interval="preserveStartEnd"
                                        />
                                        <YAxis 
                                            tick={{ fontSize: 12 }}
                                            label={{ value: 'Partitions', angle: -90, position: 'insideLeft' }}
                                        />
                                        <Tooltip 
                                            contentStyle={tooltipStyle}
                                            labelFormatter={(value) => `Time: ${value}`}
                                        />
                                        <Legend />
                                        <Area 
                                            type="monotone" 
                                            dataKey="leaderPartitions" 
                                            stroke="#f59e0b" 
                                            fill="#f59e0b"
                                            fillOpacity={0.6}
                                            name="Leader Partitions" 
                                        />
                                        <Area 
                                            type="monotone" 
                                            dataKey="replicaPartitions" 
                                            stroke="#6366f1" 
                                            fill="#6366f1"
                                            fillOpacity={0.6}
                                            name="Replica Partitions" 
                                        />
                                    </AreaChart>
                                </ResponsiveContainer>
                            </CardContent>
                        </Card>
                    </TabsContent>
                </Tabs>
            </CardContent>
        </Card>
    );
}

import { useGraphQLQuery } from '@/hooks/useGraphQLQuery';
import { GET_CLUSTER_METRICS } from '@/graphql/queries';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, AreaChart, Area } from 'recharts';
import { format } from 'date-fns';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

interface ClusterMetricsChartProps {
  clusterId: string;
  timeRange: { startTime: number; endTime: number };
  limit?: number;
}

export function ClusterMetricsChart({ clusterId, timeRange, limit = 1000 }: ClusterMetricsChartProps) {
  const { data, isLoading, error } = useGraphQLQuery(GET_CLUSTER_METRICS, {
    clusterId,
    timeRange,
    limit
  });

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Cluster Metrics</CardTitle>
          <CardDescription>Loading cluster metrics...</CardDescription>
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
          <CardTitle>Cluster Metrics</CardTitle>
          <CardDescription>Error loading metrics</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-destructive">Failed to load metrics: {error.message}</p>
        </CardContent>
      </Card>
    );
  }

  const metrics = data?.clusterMetrics || [];
  
  if (metrics.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Cluster Metrics</CardTitle>
          <CardDescription>No metrics available</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">Metrics will appear here once collection starts.</p>
        </CardContent>
      </Card>
    );
  }

  const latestMetric = metrics[metrics.length - 1];
  const chartData = metrics.map((metric: { timestamp: number; totalMessagesPerSecond?: number | null; totalBytesPerSecond?: number | null; totalTopics?: number | null; totalPartitions?: number | null; brokerCount?: number | null; activeBrokerCount?: number | null; isHealthy?: boolean | null }) => ({
    time: format(new Date(metric.timestamp), 'HH:mm:ss'),
    timestamp: metric.timestamp,
    totalMessagesPerSecond: metric.totalMessagesPerSecond || 0,
    totalBytesPerSecond: metric.totalBytesPerSecond || 0,
    totalTopics: metric.totalTopics || 0,
    totalPartitions: metric.totalPartitions || 0,
    brokerCount: metric.brokerCount || 0,
    activeBrokerCount: metric.activeBrokerCount || 0,
    isHealthy: metric.isHealthy,
  }));

  return (
    <div className="space-y-4">
      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Total Topics</CardDescription>
            <CardTitle className="text-2xl">{latestMetric.totalTopics}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Total Partitions</CardDescription>
            <CardTitle className="text-2xl">{latestMetric.totalPartitions}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Active Brokers</CardDescription>
            <CardTitle className="text-2xl">
              {latestMetric.activeBrokerCount} / {latestMetric.brokerCount}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Health Status</CardDescription>
            <CardTitle>
              <Badge variant={latestMetric.isHealthy ? "default" : "destructive"}>
                {latestMetric.isHealthy ? "Healthy" : "Unhealthy"}
              </Badge>
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Cluster Metrics</CardTitle>
          <CardDescription>Performance metrics and statistics over time</CardDescription>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="throughput" className="w-full">
            <TabsList className="grid w-full grid-cols-3">
              <TabsTrigger value="throughput">Throughput</TabsTrigger>
              <TabsTrigger value="dataTransfer">Data Transfer</TabsTrigger>
              <TabsTrigger value="resources">Resources</TabsTrigger>
            </TabsList>

            <TabsContent value="throughput" className="mt-4">
              <Card>
                <CardHeader>
                  <CardTitle>Cluster Throughput</CardTitle>
                  <CardDescription>Total messages per second across all topics</CardDescription>
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
                        label={{ value: 'Messages/sec', angle: -90, position: 'insideLeft' }}
                      />
                      <Tooltip 
                        contentStyle={{ backgroundColor: 'hsl(var(--background))', border: '1px solid hsl(var(--border))' }}
                        labelFormatter={(value) => `Time: ${value}`}
                        formatter={(value: number) => [value.toLocaleString(), 'Messages/sec']}
                      />
                      <Legend />
                      <Area 
                        type="monotone" 
                        dataKey="totalMessagesPerSecond" 
                        stroke="#8884d8" 
                        fill="#8884d8" 
                        fillOpacity={0.6}
                        name="Total Messages/sec" 
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="dataTransfer" className="mt-4">
              <Card>
                <CardHeader>
                  <CardTitle>Cluster Data Transfer</CardTitle>
                  <CardDescription>Total bytes per second across all topics</CardDescription>
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
                        label={{ value: 'Bytes/sec', angle: -90, position: 'insideLeft' }}
                      />
                      <Tooltip 
                        contentStyle={{ backgroundColor: 'hsl(var(--background))', border: '1px solid hsl(var(--border))' }}
                        labelFormatter={(value) => `Time: ${value}`}
                        formatter={(value: number) => {
                          const bytes = value as number;
                          if (bytes >= 1024 * 1024 * 1024) {
                            return [`${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB/s`, 'Bytes/sec'];
                          } else if (bytes >= 1024 * 1024) {
                            return [`${(bytes / (1024 * 1024)).toFixed(2)} MB/s`, 'Bytes/sec'];
                          } else if (bytes >= 1024) {
                            return [`${(bytes / 1024).toFixed(2)} KB/s`, 'Bytes/sec'];
                          }
                          return [`${bytes} B/s`, 'Bytes/sec'];
                        }}
                      />
                      <Legend />
                      <Line 
                        type="monotone" 
                        dataKey="totalBytesPerSecond" 
                        stroke="#82ca9d" 
                        name="Total Bytes/sec" 
                        strokeWidth={2}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="resources" className="mt-4">
              <Card>
                <CardHeader>
                  <CardTitle>Cluster Resources</CardTitle>
                  <CardDescription>Topics and partitions over time</CardDescription>
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
                      />
                      <Tooltip 
                        contentStyle={{ backgroundColor: 'hsl(var(--background))', border: '1px solid hsl(var(--border))' }}
                        labelFormatter={(value) => `Time: ${value}`}
                      />
                      <Legend />
                      <Line 
                        type="monotone" 
                        dataKey="totalTopics" 
                        stroke="#8884d8" 
                        name="Total Topics" 
                        strokeWidth={2}
                      />
                      <Line 
                        type="monotone" 
                        dataKey="totalPartitions" 
                        stroke="#82ca9d" 
                        name="Total Partitions" 
                        strokeWidth={2}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}


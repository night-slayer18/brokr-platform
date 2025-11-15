import { useGraphQLQuery } from '@/hooks/useGraphQLQuery';
import { GET_TOPIC_METRICS } from '@/graphql/queries';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, AreaChart, Area } from 'recharts';
import { format } from 'date-fns';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

interface TopicMetricsChartProps {
  clusterId: string;
  topicName: string;
  timeRange: { startTime: number; endTime: number };
  limit?: number;
}

export function TopicMetricsChart({ clusterId, topicName, timeRange, limit = 1000 }: TopicMetricsChartProps) {
  const { data, isLoading, error } = useGraphQLQuery(GET_TOPIC_METRICS, {
    clusterId,
    topicName,
    timeRange,
    limit
  });

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Topic Metrics</CardTitle>
          <CardDescription>Loading metrics for {topicName}...</CardDescription>
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
          <CardTitle>Topic Metrics</CardTitle>
          <CardDescription>Error loading metrics</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-destructive">Failed to load metrics: {error.message}</p>
        </CardContent>
      </Card>
    );
  }

  const metrics = data?.topicMetrics || [];
  
  if (metrics.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Topic Metrics</CardTitle>
          <CardDescription>No metrics available for {topicName}</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">Metrics will appear here once collection starts.</p>
        </CardContent>
      </Card>
    );
  }

  const chartData = metrics.map((metric: { timestamp: number; messagesPerSecondIn?: number | null; bytesPerSecondIn?: number | null; totalSizeBytes?: number | null }) => ({
    time: format(new Date(metric.timestamp), 'HH:mm:ss'),
    timestamp: metric.timestamp,
    messagesIn: metric.messagesPerSecondIn || 0,
    bytesIn: metric.bytesPerSecondIn || 0,
    totalSize: metric.totalSizeBytes || 0,
  }));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Topic Metrics - {topicName}</CardTitle>
        <CardDescription>Performance metrics and statistics over time</CardDescription>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="throughput" className="w-full">
          <TabsList className="grid w-full grid-cols-3">
            <TabsTrigger value="throughput">Throughput</TabsTrigger>
            <TabsTrigger value="dataTransfer">Data Transfer</TabsTrigger>
            <TabsTrigger value="size">Topic Size</TabsTrigger>
          </TabsList>

          <TabsContent value="throughput" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Throughput</CardTitle>
                <CardDescription>Messages per second over time</CardDescription>
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
                      dataKey="messagesIn" 
                      stroke="#8884d8" 
                      fill="#8884d8" 
                      fillOpacity={0.6}
                      name="Messages In/sec" 
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="dataTransfer" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Data Transfer</CardTitle>
                <CardDescription>Bytes per second over time</CardDescription>
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
                      dataKey="bytesIn" 
                      stroke="#8884d8" 
                      name="Bytes In/sec" 
                      strokeWidth={2}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="size" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Topic Size</CardTitle>
                <CardDescription>Total size in bytes over time</CardDescription>
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
                      label={{ value: 'Size (bytes)', angle: -90, position: 'insideLeft' }}
                    />
                    <Tooltip 
                      contentStyle={{ backgroundColor: 'hsl(var(--background))', border: '1px solid hsl(var(--border))' }}
                      labelFormatter={(value) => `Time: ${value}`}
                      formatter={(value: number) => {
                        const bytes = value as number;
                        if (bytes >= 1024 * 1024 * 1024) {
                          return [`${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`, 'Total Size'];
                        } else if (bytes >= 1024 * 1024) {
                          return [`${(bytes / (1024 * 1024)).toFixed(2)} MB`, 'Total Size'];
                        } else if (bytes >= 1024) {
                          return [`${(bytes / 1024).toFixed(2)} KB`, 'Total Size'];
                        }
                        return [`${bytes} B`, 'Total Size'];
                      }}
                    />
                    <Area 
                      type="monotone" 
                      dataKey="totalSize" 
                      stroke="#ffc658" 
                      fill="#ffc658" 
                      fillOpacity={0.6}
                      name="Total Size (bytes)" 
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


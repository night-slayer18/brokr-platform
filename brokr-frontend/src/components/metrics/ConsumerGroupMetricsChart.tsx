import { useGraphQLQuery } from '@/hooks/useGraphQLQuery';
import { GET_CONSUMER_GROUP_METRICS } from '@/graphql/queries';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, AreaChart, Area } from 'recharts';
import { format } from 'date-fns';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

interface ConsumerGroupMetricsChartProps {
  clusterId: string;
  consumerGroupId: string;
  timeRange: { startTime: number; endTime: number };
  limit?: number;
}

export function ConsumerGroupMetricsChart({ clusterId, consumerGroupId, timeRange, limit = 1000 }: ConsumerGroupMetricsChartProps) {
  const { data, isLoading, error } = useGraphQLQuery(GET_CONSUMER_GROUP_METRICS, {
    clusterId,
    consumerGroupId,
    timeRange,
    limit
  });

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Consumer Group Metrics</CardTitle>
          <CardDescription>Loading metrics for {consumerGroupId}...</CardDescription>
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
          <CardTitle>Consumer Group Metrics</CardTitle>
          <CardDescription>Error loading metrics</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-destructive">Failed to load metrics: {error.message}</p>
        </CardContent>
      </Card>
    );
  }

  const metrics = data?.consumerGroupMetrics || [];
  
  if (metrics.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Consumer Group Metrics</CardTitle>
          <CardDescription>No metrics available for {consumerGroupId}</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">Metrics will appear here once collection starts.</p>
        </CardContent>
      </Card>
    );
  }

  const chartData = metrics.map((metric: { timestamp: number; totalLag?: number | null; maxLag?: number | null; minLag?: number | null; avgLag?: number | null; memberCount?: number | null }) => ({
    time: format(new Date(metric.timestamp), 'HH:mm:ss'),
    timestamp: metric.timestamp,
    totalLag: metric.totalLag || 0,
    maxLag: metric.maxLag || 0,
    minLag: metric.minLag || 0,
    avgLag: metric.avgLag || 0,
    memberCount: metric.memberCount || 0,
  }));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Consumer Group Metrics - {consumerGroupId}</CardTitle>
        <CardDescription>Performance metrics and statistics over time</CardDescription>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="lag" className="w-full">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="lag">Consumer Lag</TabsTrigger>
            <TabsTrigger value="members">Member Count</TabsTrigger>
          </TabsList>

          <TabsContent value="lag" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Consumer Lag</CardTitle>
                <CardDescription>Lag metrics over time</CardDescription>
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
                      label={{ value: 'Lag (messages)', angle: -90, position: 'insideLeft' }}
                    />
                    <Tooltip 
                      contentStyle={{ backgroundColor: 'hsl(var(--background))', border: '1px solid hsl(var(--border))' }}
                      labelFormatter={(value) => `Time: ${value}`}
                      formatter={(value: number) => [value.toLocaleString(), 'Messages']}
                    />
                    <Legend />
                    <Area 
                      type="monotone" 
                      dataKey="totalLag" 
                      stroke="#8884d8" 
                      fill="#8884d8" 
                      fillOpacity={0.6}
                      name="Total Lag" 
                    />
                    <Area 
                      type="monotone" 
                      dataKey="maxLag" 
                      stroke="#ff7300" 
                      fill="#ff7300" 
                      fillOpacity={0.4}
                      name="Max Lag" 
                    />
                    <Area 
                      type="monotone" 
                      dataKey="avgLag" 
                      stroke="#82ca9d" 
                      fill="#82ca9d" 
                      fillOpacity={0.4}
                      name="Avg Lag" 
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="members" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Member Count</CardTitle>
                <CardDescription>Active members over time</CardDescription>
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
                      label={{ value: 'Members', angle: -90, position: 'insideLeft' }}
                    />
                    <Tooltip 
                      contentStyle={{ backgroundColor: 'hsl(var(--background))', border: '1px solid hsl(var(--border))' }}
                      labelFormatter={(value) => `Time: ${value}`}
                      formatter={(value: number) => [value, 'Members']}
                    />
                    <Line 
                      type="monotone" 
                      dataKey="memberCount" 
                      stroke="#8884d8" 
                      name="Member Count" 
                      strokeWidth={2}
                      dot={{ r: 4 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
}


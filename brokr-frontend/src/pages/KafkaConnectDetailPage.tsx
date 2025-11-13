import { useParams } from 'react-router-dom';
import { GET_KAFKA_CONNECT } from '@/graphql/queries';
import type { GetKafkaConnectQuery } from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatRelativeTime } from '@/lib/formatters';
import { CONNECTOR_STATES } from '@/lib/constants';

export default function KafkaConnectDetailPage() {
  const { kcId } = useParams<{ clusterId: string; kcId: string }>();

  const { data, isLoading: loading, error } = useGraphQLQuery<GetKafkaConnectQuery, {id: string}>(GET_KAFKA_CONNECT, 
    kcId ? {id: kcId} : undefined,
    {
      enabled: !!kcId,
    }
  );

  const kafkaConnect = data?.kafkaConnect;

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-10 w-1/2" />
        <div className="grid gap-4 md:grid-cols-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
        <Skeleton className="h-96" />
      </div>
    );
  }

  if (error) {
    return <div className="text-destructive">Error loading Kafka Connect details: {error.message}</div>;
  }

  if (!kafkaConnect) {
    return <div>Kafka Connect instance not found.</div>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
          {kafkaConnect.name}
          <Badge variant={kafkaConnect.isReachable ? "default" : "destructive"}>
            {kafkaConnect.isReachable ? "Online" : "Offline"}
          </Badge>
        </h2>
        <p className="text-muted-foreground">Details for Kafka Connect instance <span className="font-mono">{kafkaConnect.name}</span></p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>URL</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm font-mono break-all">{kafkaConnect.url}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Connection Status</CardTitle>
          </CardHeader>
          <CardContent>
            <Badge variant={kafkaConnect.isReachable ? "default" : "destructive"}>
              {kafkaConnect.isReachable ? "Online" : "Offline"}
            </Badge>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Last Checked</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm">{formatRelativeTime(kafkaConnect.lastConnectionCheck)}</p>
            {kafkaConnect.lastConnectionError && (
              <p className="text-destructive text-xs mt-1">{kafkaConnect.lastConnectionError}</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Connectors</CardTitle>
          <CardDescription>Connectors managed by this Kafka Connect instance.</CardDescription>
        </CardHeader>
        <CardContent>
          {kafkaConnect.connectors.length === 0 ? (
            <p className="text-muted-foreground">No connectors found for this Kafka Connect instance.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>State</TableHead>
                  <TableHead>Tasks</TableHead>
                  <TableHead>Config</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {kafkaConnect.connectors.map(connector => {
                  let formattedConfig = '{}';
                  try {
                    formattedConfig = JSON.stringify(JSON.parse(connector.config || '{}'), null, 2);
                  } catch {
                    // Ignore parsing errors, use raw string
                    formattedConfig = connector.config || '{}';
                  }

                  return (
                    <TableRow key={connector.name}>
                      <TableCell className="font-medium">{connector.name}</TableCell>
                      <TableCell>{connector.type}</TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            connector.state === CONNECTOR_STATES.RUNNING
                              ? "default"
                              : connector.state === CONNECTOR_STATES.FAILED
                                ? "destructive"
                                : "secondary"
                          }
                        >
                          {connector.state}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {connector.tasks.map(task => (
                          <Badge key={task.id} variant="outline" className="mr-1 mb-1">
                            Task {task.id}: {task.state}
                          </Badge>
                        ))}
                      </TableCell>
                      <TableCell>
                        <div className="border rounded-lg overflow-auto max-h-[100px] bg-muted/30">
                          <pre className="p-2 font-mono text-xs">
                            {formattedConfig}
                          </pre>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

import { useParams } from 'react-router-dom';
import { GET_KAFKA_STREAMS_APPLICATION } from '@/graphql/queries';
import type { GetKafkaStreamsApplicationQuery } from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import Editor from '@monaco-editor/react';
import { STREAMS_STATES } from '@/lib/constants';

export default function KafkaStreamsDetailPage() {
  const { ksId } = useParams<{ clusterId: string; ksId: string }>();

  const { data, isLoading: loading, error } = useGraphQLQuery<GetKafkaStreamsApplicationQuery, {id: string}>(GET_KAFKA_STREAMS_APPLICATION, 
    ksId ? {id: ksId} : undefined,
    {
      enabled: !!ksId,
    }
  );

  const kafkaStreamsApp = data?.kafkaStreamsApplication;

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
    return <div className="text-destructive">Error loading Kafka Streams Application details: {error.message}</div>;
  }

  if (!kafkaStreamsApp) {
    return <div>Kafka Streams Application not found.</div>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
          {kafkaStreamsApp.name}
          <Badge
            variant={
              kafkaStreamsApp.state === STREAMS_STATES.RUNNING
                ? "default"
                : kafkaStreamsApp.state === STREAMS_STATES.ERROR
                  ? "destructive"
                  : "secondary"
            }
          >
            {kafkaStreamsApp.state}
          </Badge>
        </h2>
        <p className="text-muted-foreground">Details for Kafka Streams Application <span className="font-mono">{kafkaStreamsApp.name}</span></p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>Application ID</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm font-mono break-all">{kafkaStreamsApp.applicationId}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Status</CardTitle>
          </CardHeader>
          <CardContent>
            <p className={kafkaStreamsApp.isActive ? 'text-green-400 font-medium' : 'text-gray-500'}>
              {kafkaStreamsApp.isActive ? 'Active' : 'Inactive'}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Topics</CardTitle>
          </CardHeader>
          <CardContent>
            {kafkaStreamsApp.topics.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {kafkaStreamsApp.topics.map(topic => (
                  <Badge key={topic} variant="secondary">{topic}</Badge>
                ))}
              </div>
            ) : (
              <p className="text-muted-foreground text-sm">No topics configured.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Configuration</CardTitle>
          <CardDescription>Kafka Streams application configuration.</CardDescription>
        </CardHeader>
        <CardContent>
          <Editor
            height="200px"
            language="json"
            value={JSON.stringify(kafkaStreamsApp.configuration, null, 2) || '{}'}
            options={{
              readOnly: true,
              minimap: { enabled: false },
              wordWrap: "on",
            }}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Threads</CardTitle>
          <CardDescription>Live thread metadata for this application.</CardDescription>
        </CardHeader>
        <CardContent>
          {kafkaStreamsApp.threads.length === 0 ? (
            <p className="text-muted-foreground">No active threads found.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Thread Name</TableHead>
                  <TableHead>State</TableHead>
                  <TableHead>Consumer Client ID</TableHead>
                  <TableHead>Tasks</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {kafkaStreamsApp.threads.map(thread => (
                  <TableRow key={thread.threadName}>
                    <TableCell className="font-medium">{thread.threadName}</TableCell>
                    <TableCell><Badge>{thread.threadState}</Badge></TableCell>
                    <TableCell>{thread.consumerClientId.join(', ')}</TableCell>
                    <TableCell>
                      {thread.tasks.map(task => (
                        <Badge key={task.taskIdString} variant="outline" className="mr-1 mb-1">
                          {task.taskIdString} ({task.taskState})
                        </Badge>
                      ))}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

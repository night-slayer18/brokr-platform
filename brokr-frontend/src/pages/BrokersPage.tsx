import { useQuery } from '@apollo/client/react';
import { useParams } from 'react-router-dom';
import { GET_CLUSTER } from '@/graphql/queries';
import type { GetClusterQuery, GetClusterVariables } from '@/graphql/types';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { BrokerNode } from '@/types';

export default function BrokersPage() {
    const { clusterId } = useParams<{ clusterId: string }>();
    const { data, loading, error } = useQuery<GetClusterQuery, GetClusterVariables>(GET_CLUSTER, {
        variables: { id: clusterId! },
        skip: !clusterId,
    });

    if (loading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-1/2" />
                <Skeleton className="h-96" />
            </div>
        );
    }

    if (error) {
        return <div className="text-destructive">Error loading broker details: {error.message}</div>;
    }

    const brokers = data?.cluster?.brokers || [];

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-3xl font-bold tracking-tight">Brokers</h2>
                <p className="text-muted-foreground">List of brokers in the cluster.</p>
            </div>
            <Card>
                <CardHeader>
                    <CardTitle>Broker Nodes</CardTitle>
                    <CardDescription>The nodes that make up the Kafka cluster.</CardDescription>
                </CardHeader>
                <CardContent>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Node ID</TableHead>
                                <TableHead>Host</TableHead>
                                <TableHead>Port</TableHead>
                                <TableHead>Rack</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {brokers.map((broker: BrokerNode) => (
                                <TableRow key={broker.id}>
                                    <TableCell className="font-mono">{broker.id}</TableCell>
                                    <TableCell>{broker.host}</TableCell>
                                    <TableCell>{broker.port}</TableCell>
                                    <TableCell>{broker.rack || 'N/A'}</TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </CardContent>
            </Card>
        </div>
    );
}

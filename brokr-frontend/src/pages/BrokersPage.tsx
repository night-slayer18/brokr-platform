import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { GET_CLUSTER } from '@/graphql/queries';
import type { GetClusterQuery } from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Server } from 'lucide-react';
import type { BrokerNode } from '@/types';

export default function BrokersPage() {
    const { clusterId } = useParams<{ clusterId: string }>();
    const [selectedBroker, setSelectedBroker] = useState<BrokerNode | null>(null);
    const { data, isLoading: loading, error } = useGraphQLQuery<GetClusterQuery, {id: string}>(GET_CLUSTER, 
        clusterId ? {id: clusterId} : undefined,
        {
            enabled: !!clusterId,
        }
    );

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
                <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-primary to-primary/80 bg-clip-text text-transparent">Brokers</h2>
                <p className="text-muted-foreground mt-2">List of brokers in the cluster.</p>
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
                                <TableRow 
                                    key={broker.id}
                                    className="cursor-pointer hover:bg-accent/50 transition-colors"
                                    onClick={() => setSelectedBroker(broker)}
                                >
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

            <Dialog open={!!selectedBroker} onOpenChange={(open) => !open && setSelectedBroker(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <Server className="h-5 w-5 text-primary" />
                            Broker Details
                        </DialogTitle>
                        <DialogDescription>
                            Detailed information about the broker node.
                        </DialogDescription>
                    </DialogHeader>
                    {selectedBroker && (
                        <div className="space-y-4 mt-4">
                            <div className="grid gap-4">
                                <div className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
                                    <span className="text-sm font-medium text-muted-foreground">Node ID</span>
                                    <Badge variant="outline" className="font-mono">{selectedBroker.id}</Badge>
                                </div>
                                <div className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
                                    <span className="text-sm font-medium text-muted-foreground">Host</span>
                                    <span className="font-mono text-sm">{selectedBroker.host}</span>
                                </div>
                                <div className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
                                    <span className="text-sm font-medium text-muted-foreground">Port</span>
                                    <span className="font-mono text-sm">{selectedBroker.port}</span>
                                </div>
                                <div className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
                                    <span className="text-sm font-medium text-muted-foreground">Rack</span>
                                    <span className="text-sm">{selectedBroker.rack || 'N/A'}</span>
                                </div>
                                <div className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
                                    <span className="text-sm font-medium text-muted-foreground">Connection String</span>
                                    <span className="font-mono text-sm">{selectedBroker.host}:{selectedBroker.port}</span>
                                </div>
                            </div>
                        </div>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}

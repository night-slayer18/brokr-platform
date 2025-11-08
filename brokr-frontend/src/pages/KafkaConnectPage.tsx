import {useState} from 'react';
import {useMutation, useQuery} from '@apollo/client/react';
import {useNavigate, useParams} from 'react-router-dom';
import {GET_KAFKA_CONNECTS} from '@/graphql/queries';
import {DELETE_KAFKA_CONNECT_MUTATION, TEST_KAFKA_CONNECT_CONNECTION_MUTATION} from '@/graphql/mutations';
import type {
    DeleteKafkaConnectMutation,
    DeleteKafkaConnectMutationVariables,
    GetKafkaConnectsQuery,
    TestKafkaConnectConnectionMutation,
    TestKafkaConnectConnectionMutationVariables
} from '@/graphql/types';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {Skeleton} from '@/components/ui/skeleton';
import {Eye, PlugZap, Plus, RefreshCw, Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {CreateKafkaConnectForm} from '@/components/kafka-connect/CreateKafkaConnectForm';
import {useAuth} from '@/hooks/useAuth';
import {formatRelativeTime} from '@/lib/formatters';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "@/components/ui/dialog";

export default function KafkaConnectPage() {
    const {clusterId} = useParams<{ clusterId: string }>();
    const navigate = useNavigate();
    const {canManageClusters} = useAuth();
    const [isCreateFormOpen, setIsCreateFormOpen] = useState(false);
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [itemToDelete, setItemToDelete] = useState<{ id: string; name: string } | null>(null);

    const {data, loading, error, refetch} = useQuery<GetKafkaConnectsQuery>(GET_KAFKA_CONNECTS, {
        variables: {clusterId: clusterId!},
        skip: !clusterId,
    });

    const [deleteKafkaConnect] = useMutation<DeleteKafkaConnectMutation, DeleteKafkaConnectMutationVariables>(DELETE_KAFKA_CONNECT_MUTATION);
    const [testConnection] = useMutation<TestKafkaConnectConnectionMutation, TestKafkaConnectConnectionMutationVariables>(TEST_KAFKA_CONNECT_CONNECTION_MUTATION);

    const openDeleteDialog = (id: string, name: string) => {
        setItemToDelete({id, name});
        setIsDeleteDialogOpen(true);
    };

    const handleDelete = async () => {
        if (!itemToDelete) return;

        try {
            await deleteKafkaConnect({variables: {id: itemToDelete.id}});
            toast.success(`Kafka Connect instance "${itemToDelete.name}" deleted successfully`);
            refetch();
        } catch (err: unknown) {
            const error = err instanceof Error ? err : {message: 'Failed to delete Kafka Connect instance'}
            toast.error(error.message || 'Failed to delete Kafka Connect instance');
        } finally {
            setIsDeleteDialogOpen(false);
            setItemToDelete(null);
        }
    };

    const handleTestConnection = async (id: string, name: string) => {
        try {
            const {data} = await testConnection({variables: {id}});
            if (data) { // Access the correct field from the mutation result
                toast.success(`Connection to "${name}" successful`);
            } else {
                toast.error(`Connection to "${name}" failed`);
            }
            refetch();
        } catch (err: unknown) {
            const error = err instanceof Error ? err : {message: 'Connection test failed'}
            toast.error(error.message || 'Connection test failed');
        }
    };

    if (!clusterId) {
        return <div className="text-destructive">Cluster ID is missing. Please select a cluster.</div>;
    }

    if (loading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-64"/>
                <Skeleton className="h-4 w-96"/>
                <div className="grid gap-4">
                    {[1, 2, 3].map((i) => (
                        <Skeleton key={i} className="h-24"/>
                    ))}
                </div>
            </div>
        );
    }

    if (error) {
        return <div className="text-destructive">Error loading Kafka Connect instances: {error.message}</div>;
    }

    const kafkaConnects = data?.kafkaConnects || [];

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                        Kafka Connect
                    </h2>
                    <p className="text-muted-foreground mt-2">
                        Manage Kafka Connect instances for cluster <span className="font-mono">{clusterId}</span>
                    </p>
                </div>
                {canManageClusters() && (
                    <Button
                        onClick={() => setIsCreateFormOpen(true)}
                        className="bg-gradient-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
                    >
                        <Plus className="mr-2 h-4 w-4"/>
                        Add Kafka Connect
                    </Button>
                )}
            </div>

            {kafkaConnects.length === 0 ? (
                <Card className="border-dashed border-2 border-primary/30 bg-card/30">
                    <CardContent className="flex flex-col items-center justify-center py-16">
                        <div className="relative mb-6">
                            <div className="absolute inset-0 bg-primary/20 rounded-full blur-2xl"></div>
                            <PlugZap className="relative h-16 w-16 text-primary"/>
                        </div>
                        <h3 className="text-xl font-semibold mb-2 text-foreground">No Kafka Connect instances found</h3>
                        <p className="text-muted-foreground text-center max-w-md mb-6">
                            Get started by adding your first Kafka Connect instance to this Kafka cluster.
                        </p>
                        {canManageClusters() && (
                            <Button
                                onClick={() => setIsCreateFormOpen(true)}
                                className="bg-gradient-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
                            >
                                <Plus className="mr-2 h-4 w-4"/>
                                Add Your First Kafka Connect
                            </Button>
                        )}
                    </CardContent>
                </Card>
            ) : (
                <Card>
                    <CardHeader>
                        <CardTitle>All Kafka Connect Instances</CardTitle>
                        <CardDescription>A list of all configured Kafka Connect instances.</CardDescription>
                    </CardHeader>
                    <CardContent className="p-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Name</TableHead>
                                    <TableHead>URL</TableHead>
                                    <TableHead>Status</TableHead>
                                    <TableHead>Last Checked</TableHead>
                                    <TableHead>Connectors</TableHead>
                                    <TableHead className="text-right">Actions</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {kafkaConnects.map((kc) => (
                                    <TableRow key={kc.id}>
                                        <TableCell className="font-medium">{kc.name}</TableCell>
                                        <TableCell className="font-mono text-xs">{kc.url}</TableCell>
                                        <TableCell>
                                            <Badge variant={kc.isReachable ? "default" : "destructive"}>
                                                {kc.isReachable ? "Online" : "Offline"}
                                            </Badge>
                                        </TableCell>
                                        <TableCell>{formatRelativeTime(kc.lastConnectionCheck)}</TableCell>
                                        <TableCell>{kc.connectors.length}</TableCell>
                                        <TableCell className="text-right">
                                            <div className="flex justify-end gap-2">
                                                <Button
                                                    variant="outline"
                                                    size="sm"
                                                    onClick={() => navigate(`/clusters/${clusterId}/kafka-connect/${kc.id}`)}
                                                >
                                                    <Eye className="h-4 w-4"/>
                                                </Button>
                                                {canManageClusters() && (
                                                    <>
                                                        <Button
                                                            variant="outline"
                                                            size="sm"
                                                            onClick={() => handleTestConnection(kc.id, kc.name)}
                                                        >
                                                            <RefreshCw className="h-4 w-4"/>
                                                        </Button>
                                                        <Button
                                                            variant="destructive"
                                                            size="sm"
                                                            onClick={() => openDeleteDialog(kc.id, kc.name)}
                                                        >
                                                            <Trash2 className="h-4 w-4"/>
                                                        </Button>
                                                    </>
                                                )}
                                            </div>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>
            )}

            <CreateKafkaConnectForm
                clusterId={clusterId!}
                isOpen={isCreateFormOpen}
                onOpenChange={setIsCreateFormOpen}
                onKafkaConnectCreated={refetch}
            />

            <Dialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Delete {itemToDelete?.name}?</DialogTitle>
                        <DialogDescription>
                            This action cannot be undone. This will permanently delete the Kafka Connect instance.
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsDeleteDialogOpen(false)}>
                            Cancel
                        </Button>
                        <Button variant="destructive" onClick={handleDelete}>
                            Delete
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

import { useState } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import { useParams, useNavigate } from 'react-router-dom';
import { GET_KAFKA_STREAMS } from '@/graphql/queries';
import { DELETE_KAFKA_STREAMS_APPLICATION_MUTATION } from '@/graphql/mutations';
import type { DeleteKafkaStreamsApplicationMutation, DeleteKafkaStreamsApplicationMutationVariables, GetKafkaStreamsQuery } from '@/graphql/types';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Plus, Trash2, Eye, Zap } from 'lucide-react';
import { toast } from 'sonner';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { CreateKafkaStreamsApplicationForm } from '@/components/kafka-streams/CreateKafkaStreamsApplicationForm';
import { useAuth } from '@/hooks/useAuth';
import { STREAMS_STATES } from '@/lib/constants';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";

export default function KafkaStreamsPage() {
  const { clusterId } = useParams<{ clusterId: string }>();
  const navigate = useNavigate();
  const { canManageClusters } = useAuth();
  const [isCreateFormOpen, setIsCreateFormOpen] = useState(false);
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
  const [itemToDelete, setItemToDelete] = useState<{ id: string; name: string } | null>(null);

  const { data, loading, error, refetch } = useQuery<GetKafkaStreamsQuery>(GET_KAFKA_STREAMS, {
    variables: { clusterId: clusterId! },
    skip: !clusterId,
  });

  const [deleteKafkaStreamsApplication] = useMutation<DeleteKafkaStreamsApplicationMutation, DeleteKafkaStreamsApplicationMutationVariables>(DELETE_KAFKA_STREAMS_APPLICATION_MUTATION);

  const openDeleteDialog = (id: string, name: string) => {
    setItemToDelete({ id, name });
    setIsDeleteDialogOpen(true);
  };

  const handleDelete = async () => {
    if (!itemToDelete) return;

    try {
      await deleteKafkaStreamsApplication({ variables: { id: itemToDelete.id } });
      toast.success(`Kafka Streams Application "${itemToDelete.name}" deleted successfully`);
      refetch();
    } catch (err: any) {
      toast.error(err.message || 'Failed to delete Kafka Streams Application');
    } finally {
      setIsDeleteDialogOpen(false);
      setItemToDelete(null);
    }
  };

  if (!clusterId) {
    return <div className="text-destructive">Cluster ID is missing. Please select a cluster.</div>;
  }

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-4 w-96" />
        <div className="grid gap-4">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return <div className="text-destructive">Error loading Kafka Streams applications: {error.message}</div>;
  }

  const kafkaStreamsApplications = data?.kafkaStreamsApplications || [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-primary to-primary/80 bg-clip-text text-transparent">
            Kafka Streams
          </h2>
          <p className="text-muted-foreground mt-2">
            Manage Kafka Streams Applications for cluster <span className="font-mono">{clusterId}</span>
          </p>
        </div>
        {canManageClusters() && (
          <Button
            onClick={() => setIsCreateFormOpen(true)}
            className="bg-gradient-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Application
          </Button>
        )}
      </div>

      {kafkaStreamsApplications.length === 0 ? (
        <Card className="border-dashed border-2 border-primary/30 bg-card/30">
          <CardContent className="flex flex-col items-center justify-center py-16">
            <div className="relative mb-6">
              <div className="absolute inset-0 bg-primary/20 rounded-full blur-2xl"></div>
              <Zap className="relative h-16 w-16 text-primary" />
            </div>
            <h3 className="text-xl font-semibold mb-2 text-foreground">No Kafka Streams Applications found</h3>
            <p className="text-muted-foreground text-center max-w-md mb-6">
              Get started by adding your first Kafka Streams Application to this Kafka cluster.
            </p>
            {canManageClusters() && (
              <Button
                onClick={() => setIsCreateFormOpen(true)}
                className="bg-gradient-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
              >
                <Plus className="mr-2 h-4 w-4" />
                Add Your First Application
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>All Kafka Streams Applications</CardTitle>
            <CardDescription>A list of all configured Kafka Streams Applications.</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Application ID</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Topics</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {kafkaStreamsApplications.map((app) => (
                  <TableRow key={app.id}>
                    <TableCell className="font-medium">{app.name}</TableCell>
                    <TableCell className="font-mono text-xs">{app.applicationId}</TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          app.state === STREAMS_STATES.RUNNING
                            ? "default"
                            : app.state === STREAMS_STATES.ERROR
                              ? "destructive"
                              : "secondary"
                        }
                      >
                        {app.state}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {app.topics.length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {app.topics.map(topic => (
                            <Badge key={topic} variant="secondary">{topic}</Badge>
                          ))}
                        </div>
                      ) : (
                        "N/A"
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => navigate(`/clusters/${clusterId}/kafka-streams/${app.id}`)}
                        >
                          <Eye className="h-4 w-4" />
                        </Button>
                        {canManageClusters() && (
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => openDeleteDialog(app.id, app.name)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
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

      <CreateKafkaStreamsApplicationForm
        clusterId={clusterId!}
        isOpen={isCreateFormOpen}
        onOpenChange={setIsCreateFormOpen}
        onKafkaStreamsApplicationCreated={refetch}
      />

      <Dialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
          <DialogContent>
              <DialogHeader>
                  <DialogTitle>Delete {itemToDelete?.name}?</DialogTitle>
                  <DialogDescription>
                      This action cannot be undone. This will permanently delete the Kafka Streams Application.
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

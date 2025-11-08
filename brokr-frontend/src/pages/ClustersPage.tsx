import {useMutation, useQuery} from '@apollo/client/react'
import {useNavigate} from 'react-router-dom'
import {DELETE_CLUSTER_MUTATION, TEST_CLUSTER_CONNECTION_MUTATION} from '@/graphql/mutations'
import type {
    DeleteClusterMutation,
    DeleteClusterMutationVariables,
    GetClustersQuery,
    GetOrganizationsQuery,
    TestClusterConnectionMutation,
    TestClusterConnectionMutationVariables
} from '@/graphql/types'
import type {KafkaCluster} from '@/types'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Badge} from '@/components/ui/badge'
import {Skeleton} from '@/components/ui/skeleton'
import {Eye, Plus, RefreshCw, Server, Trash2} from 'lucide-react'
import {toast} from 'sonner'
import {useState} from "react";
import {useAuth} from "@/hooks/useAuth";
import {GET_CLUSTERS, GET_ORGANIZATIONS} from "@/graphql/queries";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue,} from "@/components/ui/select";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";

interface ClusterCardProps {
    cluster: KafkaCluster
    onDelete: (id: string) => void
    onTest: (id: string) => void
    canManage: boolean
}

function ClusterCard({cluster, onDelete, onTest, canManage}: ClusterCardProps) {
    const navigate = useNavigate()

    return (
        <Card
            className="group hover:shadow-xl hover:shadow-primary/10 transition-all duration-300 border-border/50 hover:border-primary/50 bg-card/50 backdrop-blur-sm">
            <CardHeader>
                <div className="flex items-start justify-between">
                    <div className="space-y-1 flex-1">
                        <CardTitle className="text-xl flex items-center gap-2">
                            <Server className="h-5 w-5 text-primary"/>
                            <span className="group-hover:text-primary transition-colors">{cluster.name}</span>
                        </CardTitle>
                        <CardDescription className="text-sm">{cluster.bootstrapServers}</CardDescription>
                    </div>
                    <Badge variant={cluster.isReachable ? "default" : "destructive"}
                           className={cluster.isReachable ? "bg-gradient-to-r from-green-500 to-emerald-600" : ""}>
                        {cluster.isReachable ? "Online" : "Offline"}
                    </Badge>
                </div>
            </CardHeader>
            <CardContent>
                <div className="space-y-4">
                    <div className="space-y-2 text-sm">
                        <div className="flex justify-between items-center p-2 rounded-lg bg-secondary/30">
                            <span className="text-muted-foreground">Protocol:</span>
                            <span
                                className="font-medium text-foreground">{cluster.securityProtocol || 'PLAINTEXT'}</span>
                        </div>
                        <div className="flex justify-between items-center p-2 rounded-lg bg-secondary/30">
                            <span className="text-muted-foreground">Status:</span>
                            <span className={cluster.isActive ? 'text-green-400 font-medium' : 'text-gray-500'}>
                                {cluster.isActive ? 'Active' : 'Inactive'}
                            </span>
                        </div>
                    </div>

                    <div className="flex gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            className="flex-1 border-primary/30 hover:bg-primary/10 hover:border-primary transition-all"
                            onClick={() => navigate(`/clusters/${cluster.id}`)}
                        >
                            <Eye className="h-4 w-4 mr-2"/>
                            View
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            className="border-secondary/30 hover:bg-secondary/10 hover:border-secondary transition-all"
                            onClick={() => onTest(cluster.id)}
                        >
                            <RefreshCw className="h-4 w-4"/>
                        </Button>
                        {canManage && (
                            <Button
                                variant="outline"
                                size="sm"
                                className="border-destructive/30 hover:bg-destructive/10 hover:border-destructive transition-all"
                                onClick={() => onDelete(cluster.id)}
                            >
                                <Trash2 className="h-4 w-4"/>
                            </Button>
                        )}
                    </div>
                </div>
            </CardContent>
        </Card>
    )
}

export default function ClustersPage() {
    const navigate = useNavigate();
    const {user, canManageClusters} = useAuth();
    const isSuperAdmin = user?.role === 'SUPER_ADMIN';

    // For SUPER_ADMIN, this will be null until they select an org. For ADMIN, it's set from their user profile.
    const [selectedOrgId, setSelectedOrgId] = useState<string | null>(isSuperAdmin ? null : user?.organizationId || null);
    const [clusterToDelete, setClusterToDelete] = useState<string | null>(null);

    const {
        data: organizationsData,
        loading: organizationsLoading
    } = useQuery<GetOrganizationsQuery>(GET_ORGANIZATIONS, {
        skip: !isSuperAdmin,
    });

    // Determine the final orgId to use in the cluster query.
    const orgIdForQuery = isSuperAdmin ? selectedOrgId : user?.organizationId;

    const {data, loading, refetch} = useQuery<GetClustersQuery>(GET_CLUSTERS, {
        skip: !orgIdForQuery, // Skip if no org is selected/available
        variables: {organizationId: orgIdForQuery},
    });

    const [deleteCluster] = useMutation<DeleteClusterMutation, DeleteClusterMutationVariables>(DELETE_CLUSTER_MUTATION);
    const [testConnection] = useMutation<TestClusterConnectionMutation, TestClusterConnectionMutationVariables>(TEST_CLUSTER_CONNECTION_MUTATION);

    const handleDeleteClick = (id: string) => {
        setClusterToDelete(id);
    };

    const handleDeleteConfirm = async () => {
        if (!clusterToDelete) return;

        try {
            await deleteCluster({variables: {id: clusterToDelete}});
            toast.success('Cluster deleted successfully');
            refetch();
        } catch (error: unknown) {
            const err = error instanceof Error ? error : {message: 'Failed to delete cluster'}
            toast.error(err.message || 'Failed to delete cluster');
        } finally {
            setClusterToDelete(null);
        }
    };

    const handleTest = async (id: string) => {
        try {
            const result = await testConnection({variables: {id}});
            if (result.data?.testClusterConnection) {
                toast.success('Connection test successful');
            } else {
                toast.error('Connection test failed');
            }
            await refetch();
        } catch (error: unknown) {
            const err = error instanceof Error ? error : {message: 'Connection test failed'}
            toast.error(err.message || 'Connection test failed');
        }
    };

    const isLoading = loading || organizationsLoading;
    const clusters = data?.clusters;

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                        Kafka Clusters
                    </h2>
                    <p className="text-muted-foreground mt-2">
                        Manage and monitor your Kafka clusters
                    </p>
                </div>
                {canManageClusters() && (
                    <Button
                        onClick={() => navigate('/clusters/new')}
                        className="bg-gradient-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
                    >
                        <Plus className="mr-2 h-4 w-4"/>
                        Add Cluster
                    </Button>
                )}
            </div>

            <Card>
                <CardHeader className="flex-row items-center justify-between">
                    <div className="space-y-1">
                        <CardTitle>Cluster List</CardTitle>
                        <CardDescription>
                            {isSuperAdmin && !selectedOrgId
                                ? "Select an organization to view its clusters."
                                : "A list of clusters in the organization."
                            }
                        </CardDescription>
                    </div>
                    {isSuperAdmin && (
                        <div className="w-full md:w-1/3">
                            <Select onValueChange={setSelectedOrgId} value={selectedOrgId || undefined}>
                                <SelectTrigger id="organization-select">
                                    <SelectValue placeholder="Select an organization"/>
                                </SelectTrigger>
                                <SelectContent>
                                    {organizationsData?.organizations?.map(org => (
                                        <SelectItem key={org.id} value={org.id}>{org.name}</SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                    )}
                </CardHeader>
                <CardContent>
                    {isLoading ? (
                        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3 pt-4">
                            {[1, 2, 3].map((i) => (
                                <Skeleton key={i} className="h-64"/>
                            ))}
                        </div>
                    ) : (clusters || []).length === 0 ? (
                        <div
                            className="flex flex-col items-center justify-center py-16 border-dashed border-2 rounded-lg">
                            <div className="relative mb-6">
                                <div className="absolute inset-0 bg-primary/20 rounded-full blur-2xl"></div>
                                <Server className="relative h-16 w-16 text-primary"/>
                            </div>
                            <h3 className="text-xl font-semibold mb-2 text-foreground">No clusters found</h3>
                            <p className="text-muted-foreground text-center max-w-md mb-6">
                                {isSuperAdmin && !selectedOrgId
                                    ? "Please select an organization to view its clusters."
                                    : "Get started by adding your first Kafka cluster to begin monitoring and management."
                                }
                            </p>
                            {canManageClusters() && (
                                <Button
                                    onClick={() => navigate('/clusters/new')}
                                    className="bg-gradient-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
                                >
                                    <Plus className="mr-2 h-4 w-4"/>
                                    Add Cluster
                                </Button>
                            )}
                        </div>
                    ) : (
                        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3 pt-4">
                            {(clusters || []).map((cluster: KafkaCluster) => (
                                <ClusterCard
                                    key={cluster.id}
                                    cluster={cluster}
                                    onDelete={handleDeleteClick}
                                    onTest={handleTest}
                                    canManage={canManageClusters()}
                                />
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Delete Confirmation Dialog */}
            <AlertDialog open={!!clusterToDelete} onOpenChange={(open) => !open && setClusterToDelete(null)}>
                <AlertDialogContent className="sm:max-w-[500px]">
                    <AlertDialogHeader className="space-y-5 text-center sm:text-center">
                        <div className="flex justify-center">
                            <div
                                className="flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10 ring-8 ring-destructive/20">
                                <Trash2 className="h-8 w-8 text-destructive"/>
                            </div>
                        </div>
                        <div className="space-y-2">
                            <AlertDialogTitle className="text-2xl font-bold">Delete Cluster</AlertDialogTitle>
                            <AlertDialogDescription className="text-base leading-relaxed px-6">
                                Are you sure you want to delete this cluster? This action <span
                                className="font-semibold text-destructive">cannot be undone</span>.
                                <br/>
                                <br/>
                                All associated data and configurations will be permanently removed from the system.
                            </AlertDialogDescription>
                        </div>
                    </AlertDialogHeader>
                    <AlertDialogFooter className="gap-3 sm:gap-3 flex-col sm:flex-row">
                        <AlertDialogCancel className="w-full sm:flex-1">Cancel</AlertDialogCancel>
                        <AlertDialogAction
                            onClick={handleDeleteConfirm}
                            className="w-full sm:flex-1 bg-destructive text-destructive-foreground hover:bg-destructive/90 shadow-lg shadow-destructive/50"
                        >
                            <Trash2 className="mr-2 h-4 w-4"/>
                            Delete Cluster
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}
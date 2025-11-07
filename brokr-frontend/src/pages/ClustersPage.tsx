import {useMutation, useQuery} from '@apollo/client/react'
import {GET_CLUSTERS} from '@/graphql/queries'
import {DELETE_CLUSTER_MUTATION, TEST_CLUSTER_CONNECTION_MUTATION} from '@/graphql/mutations'
import type {
    DeleteClusterMutation,
    DeleteClusterMutationVariables,
    GetClustersQuery,
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
import {useNavigate} from 'react-router-dom'

interface ClusterCardProps {
    cluster: KafkaCluster
    onDelete: (id: string) => void
    onTest: (id: string) => void
}

function ClusterCard({cluster, onDelete, onTest}: ClusterCardProps) {
    const navigate = useNavigate()

    return (
        <Card
            className="group hover:shadow-xl hover:shadow-orange-500/10 transition-all duration-300 border-border/50 hover:border-orange-500/50 bg-card/50 backdrop-blur-sm">
            <CardHeader>
                <div className="flex items-start justify-between">
                    <div className="space-y-1 flex-1">
                        <CardTitle className="text-xl flex items-center gap-2">
                            <Server className="h-5 w-5 text-orange-500"/>
                            <span className="group-hover:text-orange-400 transition-colors">{cluster.name}</span>
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
                            className="flex-1 border-orange-500/30 hover:bg-orange-500/10 hover:border-orange-500 transition-all"
                            onClick={() => navigate(`/clusters/${cluster.id}/topics`)}
                        >
                            <Eye className="h-4 w-4 mr-2"/>
                            View
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            className="border-teal-500/30 hover:bg-teal-500/10 hover:border-teal-500 transition-all"
                            onClick={() => onTest(cluster.id)}
                        >
                            <RefreshCw className="h-4 w-4"/>
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            className="border-red-500/30 hover:bg-red-500/10 hover:border-red-500 transition-all"
                            onClick={() => onDelete(cluster.id)}
                        >
                            <Trash2 className="h-4 w-4"/>
                        </Button>
                    </div>
                </div>
            </CardContent>
        </Card>
    )
}

export default function ClustersPage() {
    const navigate = useNavigate()
    const {data, loading, refetch} = useQuery<GetClustersQuery>(GET_CLUSTERS)
    const [deleteCluster] = useMutation<DeleteClusterMutation, DeleteClusterMutationVariables>(DELETE_CLUSTER_MUTATION)
    const [testConnection] = useMutation<TestClusterConnectionMutation, TestClusterConnectionMutationVariables>(TEST_CLUSTER_CONNECTION_MUTATION)

    const handleDelete = async (id: string) => {
        if (!confirm('Are you sure you want to delete this cluster?')) return

        try {
            await deleteCluster({variables: {id}})
            toast.success('Cluster deleted successfully')
            refetch()
        } catch (error: any) {
            toast.error(error.message || 'Failed to delete cluster')
        }
    }

    const handleTest = async (id: string) => {
        try {
            const result = await testConnection({variables: {id}})
            if (result.data?.testClusterConnection) {
                toast.success('Connection test successful')
            } else {
                toast.error('Connection test failed')
            }
            await refetch()
        } catch (error: any) {
            toast.error(error.message || 'Connection test failed')
        }
    }

    if (loading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-64"/>
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                    {[1, 2, 3].map((i) => (
                        <Skeleton key={i} className="h-64"/>
                    ))}
                </div>
            </div>
        )
    }

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-orange-400 to-teal-400 bg-clip-text text-transparent">
                        Kafka Clusters
                    </h2>
                    <p className="text-muted-foreground mt-2">
                        Manage and monitor your Kafka clusters
                    </p>
                </div>
                <Button
                    onClick={() => navigate('/clusters/new')}
                    className="bg-gradient-to-r from-orange-600 to-teal-600 hover:from-orange-700 hover:to-teal-700 shadow-lg shadow-orange-500/50"
                >
                    <Plus className="mr-2 h-4 w-4"/>
                    Add Cluster
                </Button>
            </div>

            {data?.clusters?.length === 0 ? (
                <Card className="border-dashed border-2 border-orange-500/30 bg-card/30">
                    <CardContent className="flex flex-col items-center justify-center py-16">
                        <div className="relative mb-6">
                            <div className="absolute inset-0 bg-orange-500/20 rounded-full blur-2xl"></div>
                            <Server className="relative h-16 w-16 text-orange-400"/>
                        </div>
                        <h3 className="text-xl font-semibold mb-2 text-foreground">No clusters found</h3>
                        <p className="text-muted-foreground text-center max-w-md mb-6">
                            Get started by adding your first Kafka cluster to begin monitoring and management.
                        </p>
                        <Button
                            onClick={() => navigate('/clusters/new')}
                            className="bg-gradient-to-r from-orange-600 to-teal-600 hover:from-orange-700 hover:to-teal-700 shadow-lg shadow-orange-500/50"
                        >
                            <Plus className="mr-2 h-4 w-4"/>
                            Add Your First Cluster
                        </Button>
                    </CardContent>
                </Card>
            ) : (
                <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
                    {data?.clusters?.map((cluster) => (
                        <ClusterCard
                            key={cluster.id}
                            cluster={cluster}
                            onDelete={handleDelete}
                            onTest={handleTest}
                        />
                    ))}
                </div>
            )}
        </div>
    )
}
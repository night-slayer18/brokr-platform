import { useParams, useNavigate } from 'react-router-dom';
import { GET_CLUSTER, GET_LATEST_BROKER_METRICS } from '@/graphql/queries';
import type { GetClusterQuery } from '@/graphql/types';
import { useGraphQLQuery } from '@/hooks/useGraphQLQuery';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { 
    RefreshCw, 
    Server, 
    AlertTriangle,
    CheckCircle2,
    XCircle
} from 'lucide-react';
import { BrokerHealthGrid } from '@/components/metrics/BrokerHealthGrid';
import type { BrokerNode, BrokerMetrics } from '@/types';

export default function BrokersPage() {
    const { clusterId } = useParams<{ clusterId: string }>();
    const navigate = useNavigate();

    const { 
        data: clusterData, 
        isLoading: clusterLoading, 
        error: clusterError 
    } = useGraphQLQuery<GetClusterQuery, {id: string}>(
        GET_CLUSTER, 
        clusterId ? { id: clusterId } : undefined,
        { enabled: !!clusterId }
    );

    const { 
        data: metricsData, 
        isLoading: metricsLoading,
        isFetching: metricsFetching,
        refetch: refetchMetrics
    } = useGraphQLQuery(
        GET_LATEST_BROKER_METRICS, 
        clusterId ? { clusterId } : undefined,
        { 
            enabled: !!clusterId,
            refetchInterval: 30000 // Auto-refresh every 30 seconds
        }
    );

    if (clusterLoading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-1/2" />
                <Skeleton className="h-96" />
            </div>
        );
    }

    if (clusterError) {
        return (
            <div className="text-destructive flex items-center gap-2">
                <XCircle className="h-5 w-5" />
                Error loading cluster: {clusterError.message}
            </div>
        );
    }

    const brokers = clusterData?.cluster?.brokers || [];
    const latestMetrics: BrokerMetrics[] = metricsData?.latestBrokerMetrics || [];

    // Calculate cluster health summary
    const healthyCount = latestMetrics.filter(m => m.isHealthy).length;
    const unhealthyCount = latestMetrics.filter(m => !m.isHealthy).length;
    const underReplicatedCount = latestMetrics.reduce((sum, m) => sum + (m.underReplicatedPartitions || 0), 0);
    const controller = latestMetrics.find(m => m.isController);

    const handleBrokerClick = (broker: BrokerNode) => {
        navigate(`/clusters/${clusterId}/brokers/${broker.id}`);
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                <div>
                    <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-primary to-primary/60 bg-clip-text text-transparent">
                        Broker Monitoring
                    </h2>
                    <p className="text-muted-foreground mt-2">
                        Real-time health and performance monitoring for all brokers in the cluster.
                    </p>
                </div>
                <Button 
                    variant="outline" 
                    onClick={() => refetchMetrics()}
                    disabled={metricsLoading || metricsFetching}
                >
                    <RefreshCw className={`h-4 w-4 mr-2 ${metricsFetching ? 'animate-spin' : ''}`} />
                    Refresh
                </Button>
            </div>

            {/* Cluster Health Summary */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <Card className="bg-gradient-to-br from-card to-card/80">
                    <CardContent className="pt-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm text-muted-foreground">Total Brokers</p>
                                <p className="text-3xl font-bold">{brokers.length}</p>
                            </div>
                            <div className="p-3 rounded-xl bg-primary/10">
                                <Server className="h-6 w-6 text-primary" />
                            </div>
                        </div>
                    </CardContent>
                </Card>
                <Card className="bg-gradient-to-br from-card to-card/80">
                    <CardContent className="pt-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm text-muted-foreground">Healthy</p>
                                <p className="text-3xl font-bold text-green-500">{healthyCount}</p>
                            </div>
                            <div className="p-3 rounded-xl bg-green-500/10">
                                <CheckCircle2 className="h-6 w-6 text-green-500" />
                            </div>
                        </div>
                    </CardContent>
                </Card>
                <Card className="bg-gradient-to-br from-card to-card/80">
                    <CardContent className="pt-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm text-muted-foreground">Unhealthy</p>
                                <p className={`text-3xl font-bold ${unhealthyCount > 0 ? 'text-destructive' : ''}`}>
                                    {unhealthyCount}
                                </p>
                            </div>
                            <div className={`p-3 rounded-xl ${unhealthyCount > 0 ? 'bg-destructive/10' : 'bg-muted'}`}>
                                <XCircle className={`h-6 w-6 ${unhealthyCount > 0 ? 'text-destructive' : 'text-muted-foreground'}`} />
                            </div>
                        </div>
                    </CardContent>
                </Card>
                <Card className="bg-gradient-to-br from-card to-card/80">
                    <CardContent className="pt-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm text-muted-foreground">Under-replicated</p>
                                <p className={`text-3xl font-bold ${underReplicatedCount > 0 ? 'text-yellow-500' : ''}`}>
                                    {underReplicatedCount}
                                </p>
                            </div>
                            <div className={`p-3 rounded-xl ${underReplicatedCount > 0 ? 'bg-yellow-500/10' : 'bg-muted'}`}>
                                <AlertTriangle className={`h-6 w-6 ${underReplicatedCount > 0 ? 'text-yellow-500' : 'text-muted-foreground'}`} />
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Controller Info */}
            {controller && (
                <Card className="border-yellow-500/50 bg-gradient-to-r from-yellow-500/5 to-transparent">
                    <CardContent className="py-4">
                        <div className="flex items-center gap-3">
                            <Badge variant="outline" className="text-yellow-500 border-yellow-500/50">
                                Controller
                            </Badge>
                            <span className="text-sm">
                                Broker <span className="font-mono font-medium">{controller.brokerId}</span> is the current cluster controller
                            </span>
                        </div>
                    </CardContent>
                </Card>
            )}

            {/* Broker Grid */}
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Server className="h-5 w-5" />
                        Broker Nodes
                    </CardTitle>
                    <CardDescription>
                        Click on a broker to view detailed metrics and performance history.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    {metricsLoading && !latestMetrics.length ? (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                            {[...Array(Math.min(4, brokers.length || 4))].map((_, i) => (
                                <Skeleton key={i} className="h-64" />
                            ))}
                        </div>
                    ) : (
                        <BrokerHealthGrid
                            brokers={brokers}
                            latestMetrics={latestMetrics}
                            onBrokerSelect={handleBrokerClick}
                        />
                    )}
                </CardContent>
            </Card>
        </div>
    );
}


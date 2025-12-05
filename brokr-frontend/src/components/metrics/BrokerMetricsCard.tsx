import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { 
    Server, 
    Cpu, 
    HardDrive, 
    Activity, 
    Crown,
    AlertCircle,
    CheckCircle2,
    XCircle
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { BrokerMetrics, BrokerNode } from '@/types';

interface BrokerMetricsCardProps {
    broker: BrokerNode;
    metrics?: BrokerMetrics | null;
    isSelected?: boolean;
    onClick?: () => void;
}

function formatBytes(bytes: number | null | undefined): string {
    if (bytes == null) return 'N/A';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let unitIndex = 0;
    let value = bytes;
    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex++;
    }
    return `${value.toFixed(1)} ${units[unitIndex]}`;
}

function formatBytesPerSecond(bytes: number | null | undefined): string {
    if (bytes == null) return 'N/A';
    const units = ['B/s', 'KB/s', 'MB/s', 'GB/s'];
    let unitIndex = 0;
    let value = bytes;
    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex++;
    }
    return `${value.toFixed(1)} ${units[unitIndex]}`;
}

export function BrokerMetricsCard({ broker, metrics, isSelected, onClick }: BrokerMetricsCardProps) {
    const isHealthy = metrics?.isHealthy ?? true;
    const isController = metrics?.isController ?? false;
    const cpuPercent = metrics?.cpuUsagePercent ?? 0;
    const memoryPercent = metrics?.memoryMaxBytes 
        ? ((metrics.memoryUsedBytes ?? 0) / metrics.memoryMaxBytes) * 100 
        : 0;
    
    const getHealthColor = () => {
        if (!isHealthy) return 'text-destructive';
        if ((metrics?.underReplicatedPartitions ?? 0) > 0) return 'text-yellow-500';
        return 'text-green-500';
    };

    const getHealthIcon = () => {
        if (!isHealthy) return <XCircle className="h-5 w-5" />;
        if ((metrics?.underReplicatedPartitions ?? 0) > 0) return <AlertCircle className="h-5 w-5" />;
        return <CheckCircle2 className="h-5 w-5" />;
    };

    return (
        <Card 
            className={cn(
                "cursor-pointer transition-all duration-200 hover:shadow-lg hover:border-primary/50",
                "bg-gradient-to-br from-card to-card/80",
                isSelected && "ring-2 ring-primary border-primary shadow-lg"
            )}
            onClick={onClick}
        >
            <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-2">
                    <div className="flex items-start gap-3">
                        <div className={cn(
                            "p-2 rounded-lg flex-shrink-0",
                            isController 
                                ? "bg-yellow-500/20 text-yellow-500" 
                                : "bg-primary/10 text-primary"
                        )}>
                            {isController ? <Crown className="h-5 w-5" /> : <Server className="h-5 w-5" />}
                        </div>
                        <div className="min-w-0 space-y-0.5">
                            <CardTitle className="text-lg">Broker {broker.id}</CardTitle>
                            {isController && (
                                <Badge variant="outline" className="text-yellow-500 border-yellow-500/50 text-xs">
                                    Controller
                                </Badge>
                            )}
                            <p className="text-sm text-muted-foreground font-mono truncate">
                                {broker.host}:{broker.port}
                            </p>
                        </div>
                    </div>
                    <div className={cn("flex-shrink-0", getHealthColor())}>
                        {getHealthIcon()}
                    </div>
                </div>
            </CardHeader>
            <CardContent className="space-y-4">
                {/* Resource Metrics */}
                {metrics && (
                    <>
                        {/* CPU */}
                        <div className="space-y-1.5">
                            <div className="flex items-center justify-between text-sm">
                                <div className="flex items-center gap-2 text-muted-foreground">
                                    <Cpu className="h-4 w-4" />
                                    <span>CPU</span>
                                </div>
                                <span className="font-medium">{cpuPercent.toFixed(1)}%</span>
                            </div>
                            <Progress 
                                value={cpuPercent} 
                                className={cn(
                                    "h-2",
                                    cpuPercent > 80 ? "[&>div]:bg-destructive" : 
                                    cpuPercent > 60 ? "[&>div]:bg-yellow-500" : "[&>div]:bg-green-500"
                                )}
                            />
                        </div>

                        {/* Memory */}
                        <div className="space-y-1.5">
                            <div className="flex items-center justify-between text-sm">
                                <div className="flex items-center gap-2 text-muted-foreground">
                                    <HardDrive className="h-4 w-4" />
                                    <span>Memory</span>
                                </div>
                                <span className="font-medium">
                                    {formatBytes(metrics.memoryUsedBytes)} / {formatBytes(metrics.memoryMaxBytes)}
                                </span>
                            </div>
                            <Progress 
                                value={memoryPercent} 
                                className={cn(
                                    "h-2",
                                    memoryPercent > 85 ? "[&>div]:bg-destructive" : 
                                    memoryPercent > 70 ? "[&>div]:bg-yellow-500" : "[&>div]:bg-green-500"
                                )}
                            />
                        </div>

                        {/* Throughput */}
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-1">
                                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                    <Activity className="h-3 w-3" />
                                    <span>In</span>
                                </div>
                                <p className="text-sm font-medium text-green-500">
                                    {formatBytesPerSecond(metrics.bytesInPerSecond)}
                                </p>
                            </div>
                            <div className="space-y-1">
                                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                    <Activity className="h-3 w-3" />
                                    <span>Out</span>
                                </div>
                                <p className="text-sm font-medium text-blue-500">
                                    {formatBytesPerSecond(metrics.bytesOutPerSecond)}
                                </p>
                            </div>
                        </div>

                        {/* Partitions */}
                        <div className="pt-2 border-t border-border/50">
                            <div className="grid grid-cols-2 gap-2 text-xs">
                                <div className="flex justify-between">
                                    <span className="text-muted-foreground">Leader</span>
                                    <span className="font-medium">{metrics.leaderPartitionCount ?? 0}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-muted-foreground">Replica</span>
                                    <span className="font-medium">{metrics.replicaPartitionCount ?? 0}</span>
                                </div>
                                {(metrics.underReplicatedPartitions ?? 0) > 0 && (
                                    <div className="flex justify-between col-span-2">
                                        <span className="text-yellow-500">Under-replicated</span>
                                        <span className="font-medium text-yellow-500">
                                            {metrics.underReplicatedPartitions}
                                        </span>
                                    </div>
                                )}
                            </div>
                        </div>
                    </>
                )}

                {/* No metrics fallback */}
                {!metrics && (
                    <div className="flex items-center justify-center py-4 text-muted-foreground text-sm">
                        <Activity className="h-4 w-4 mr-2 animate-pulse" />
                        Awaiting metrics...
                    </div>
                )}
            </CardContent>
        </Card>
    );
}

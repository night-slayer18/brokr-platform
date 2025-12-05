import { BrokerMetricsCard } from './BrokerMetricsCard';
import type { BrokerMetrics, BrokerNode } from '@/types';

interface BrokerHealthGridProps {
    brokers: BrokerNode[];
    latestMetrics?: BrokerMetrics[];
    selectedBrokerId?: number | null;
    onBrokerSelect?: (broker: BrokerNode) => void;
}

export function BrokerHealthGrid({ 
    brokers, 
    latestMetrics = [], 
    selectedBrokerId,
    onBrokerSelect 
}: BrokerHealthGridProps) {
    // Create a map of broker ID to metrics for quick lookup
    const metricsMap = new Map<number, BrokerMetrics>();
    latestMetrics.forEach(metric => {
        metricsMap.set(metric.brokerId, metric);
    });

    // Sort brokers: controller first, then by ID
    const sortedBrokers = [...brokers].sort((a, b) => {
        const aMetrics = metricsMap.get(a.id);
        const bMetrics = metricsMap.get(b.id);
        
        // Controller comes first
        if (aMetrics?.isController && !bMetrics?.isController) return -1;
        if (!aMetrics?.isController && bMetrics?.isController) return 1;
        
        // Then sort by ID
        return a.id - b.id;
    });

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {sortedBrokers.map((broker) => (
                <BrokerMetricsCard
                    key={broker.id}
                    broker={broker}
                    metrics={metricsMap.get(broker.id)}
                    isSelected={selectedBrokerId === broker.id}
                    onClick={() => onBrokerSelect?.(broker)}
                />
            ))}
        </div>
    );
}

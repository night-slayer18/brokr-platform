import {useParams} from 'react-router-dom';
import {GET_KSQLDB, GET_KSQLDB_SERVER_INFO} from '@/graphql/queries';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {executeGraphQL} from '@/lib/graphql-client';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {Badge} from '@/components/ui/badge';
import {formatRelativeTime} from '@/lib/formatters';
import {useEffect, useState, useCallback} from 'react';
import {toast} from "sonner";

// Temporary types until GraphQL types are generated
type GetKsqlDBQuery = {
    ksqlDB?: {
        id: string;
        name: string;
        url: string;
        isActive: boolean;
        isReachable: boolean;
        lastConnectionError?: string | null;
        lastConnectionCheck: number;
    };
};

type GetKsqlDBServerInfoQuery = {
    ksqlDBServerInfo?: string;
};

export default function KsqlDBDetailPage() {
    const {ksqlDBId} = useParams<{ clusterId: string; ksqlDBId: string }>();

    const {
        data: ksqlDBData,
        isLoading: ksqlDBLoading,
        error: ksqlDBError
    } = useGraphQLQuery<GetKsqlDBQuery, {id: string}>(GET_KSQLDB, 
        ksqlDBId ? {id: ksqlDBId} : undefined,
        {
            enabled: !!ksqlDBId,
        }
    );

    const [serverInfo, setServerInfo] = useState<string | null>(null);
    const [serverInfoLoading, setServerInfoLoading] = useState(false);

    const ksqlDB = ksqlDBData?.ksqlDB;

    const fetchServerInfo = useCallback(async () => {
        if (!ksqlDBId) return;
        
        setServerInfoLoading(true);
        try {
            const data = await executeGraphQL<GetKsqlDBServerInfoQuery>(GET_KSQLDB_SERVER_INFO, {
                ksqlDBId: ksqlDBId,
            });
            if (data && data.ksqlDBServerInfo) {
                try {
                    // Try to format as JSON if possible
                    const parsed = JSON.parse(data.ksqlDBServerInfo);
                    setServerInfo(JSON.stringify(parsed, null, 2));
                } catch {
                    // If not JSON, use as-is
                    setServerInfo(data.ksqlDBServerInfo);
                }
            }
        } catch (err: unknown) {
            const error = err instanceof Error ? err : {message: "Failed to fetch server info"};
            toast.error(error.message || "Failed to fetch server info");
            setServerInfo("Error loading server info.");
        } finally {
            setServerInfoLoading(false);
        }
    }, [ksqlDBId]);

    useEffect(() => {
        if (ksqlDB) {
            fetchServerInfo();
        }
    }, [ksqlDB, fetchServerInfo]);

    if (ksqlDBLoading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-1/2"/>
                <div className="grid gap-4 md:grid-cols-3">
                    <Skeleton className="h-24"/>
                    <Skeleton className="h-24"/>
                    <Skeleton className="h-24"/>
                </div>
                <Skeleton className="h-96"/>
            </div>
        );
    }

    if (ksqlDBError) {
        return <div className="text-destructive">Error loading ksqlDB
            details: {ksqlDBError.message}</div>;
    }

    if (!ksqlDB) {
        return <div>ksqlDB instance not found.</div>;
    }

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
                    {ksqlDB.name}
                    <Badge variant={ksqlDB.isReachable ? "default" : "destructive"}>
                        {ksqlDB.isReachable ? "Online" : "Offline"}
                    </Badge>
                </h2>
                <p className="text-muted-foreground">Details for ksqlDB instance <span
                    className="font-mono">{ksqlDB.name}</span></p>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <Card>
                    <CardHeader>
                        <CardTitle>URL</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-sm font-mono break-all">{ksqlDB.url}</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Status</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className={ksqlDB.isActive ? 'text-green-400 font-medium' : 'text-gray-500'}>
                            {ksqlDB.isActive ? 'Active' : 'Inactive'}
                        </p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Last Checked</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-sm">{formatRelativeTime(ksqlDB.lastConnectionCheck)}</p>
                        {ksqlDB.lastConnectionError && (
                            <p className="text-destructive text-xs mt-1">{ksqlDB.lastConnectionError}</p>
                        )}
                    </CardContent>
                </Card>
            </div>

            <Card>
                <CardHeader>
                    <CardTitle>Server Information</CardTitle>
                    <CardDescription>Server information from this ksqlDB instance.</CardDescription>
                </CardHeader>
                <CardContent>
                    {serverInfoLoading ? (
                        <Skeleton className="h-64 w-full"/>
                    ) : (
                        <div className="border rounded-lg overflow-auto max-h-[600px] bg-muted/30">
                            <pre className="p-4 font-mono text-sm">
                                {serverInfo || '{}'}
                            </pre>
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}


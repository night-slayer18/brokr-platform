import {useParams} from 'react-router-dom';
import {GET_KSQLDB} from '@/graphql/queries';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {Badge} from '@/components/ui/badge';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {formatRelativeTime} from '@/lib/formatters';
import {KsqlQueryEditor} from '@/components/ksqldb/KsqlQueryEditor';
import {KsqlQueryResultViewer} from '@/components/ksqldb/KsqlQueryResultViewer';
import {KsqlQueryHistory} from '@/components/ksqldb/KsqlQueryHistory';
import {KsqlStreamsTablesManagement} from '@/components/ksqldb/KsqlStreamsTablesManagement';
import {useState, useCallback} from 'react';
import {toast} from "sonner";
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation';
import {EXECUTE_KSQL_QUERY, EXECUTE_KSQL_STATEMENT} from '@/graphql/mutations';
import {Code, History, Database} from 'lucide-react';

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

interface KsqlQueryResult {
    queryId?: string;
    columns: string[];
    rows: string[][];
    executionTimeMs?: number;
    errorMessage?: string;
}

export default function KsqlDBDetailPage() {
    const {ksqlDBId} = useParams<{ clusterId: string; ksqlDBId: string }>();
    const [queryResult, setQueryResult] = useState<KsqlQueryResult | null>(null);
    const [isExecuting, setIsExecuting] = useState(false);

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

    const executeQueryMutation = useGraphQLMutation(EXECUTE_KSQL_QUERY, {
        onSuccess: (data) => {
            const result = data.executeKsqlQuery;
            setQueryResult({
                queryId: result.queryId,
                columns: result.columns || [],
                rows: result.rows || [],
                executionTimeMs: result.executionTimeMs,
                errorMessage: result.errorMessage,
            });
            setIsExecuting(false);
            toast.success('Query executed successfully');
        },
        onError: (error) => {
            setQueryResult({
                columns: [],
                rows: [],
                errorMessage: error.message || 'Query execution failed',
            });
            setIsExecuting(false);
            toast.error(`Query failed: ${error.message}`);
        },
    });

    const executeStatementMutation = useGraphQLMutation(EXECUTE_KSQL_STATEMENT, {
        onSuccess: (data) => {
            const result = data.executeKsqlStatement;
            setQueryResult({
                queryId: result.queryId,
                columns: result.columns || [],
                rows: result.rows || [],
                executionTimeMs: result.executionTimeMs,
                errorMessage: result.errorMessage,
            });
            setIsExecuting(false);
            toast.success('Statement executed successfully');
        },
        onError: (error) => {
            setQueryResult({
                columns: [],
                rows: [],
                errorMessage: error.message || 'Statement execution failed',
            });
            setIsExecuting(false);
            toast.error(`Statement failed: ${error.message}`);
        },
    });

    const handleExecuteQuery = useCallback(async (query: string) => {
        if (!ksqlDBId) {
            toast.error('ksqlDB ID is required');
            return;
        }

        setIsExecuting(true);
        setQueryResult(null);

        // Determine if it's a SELECT query (query) or a statement (CREATE, DROP, etc.)
        const trimmedQuery = query.trim().toUpperCase();
        const isSelectQuery = trimmedQuery.startsWith('SELECT') || 
                             trimmedQuery.startsWith('PRINT') ||
                             trimmedQuery.startsWith('SHOW') ||
                             trimmedQuery.startsWith('DESCRIBE') ||
                             trimmedQuery.startsWith('EXPLAIN');

        try {
            if (isSelectQuery) {
                await executeQueryMutation.mutate({
                    ksqlDBId,
                    input: {
                        query,
                        properties: {},
                    },
                });
            } else {
                await executeStatementMutation.mutate({
                    ksqlDBId,
                    input: {
                        query,
                        properties: {},
                    },
                });
            }
        } catch {
            // Error is handled by mutation callbacks
            setIsExecuting(false);
        }
    }, [ksqlDBId, executeQueryMutation, executeStatementMutation]);

    const handleCancel = useCallback(() => {
        setIsExecuting(false);
        toast.info('Query execution cancelled');
    }, []);

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
        return <div className="text-destructive">Error loading ksqlDB details: {ksqlDBError.message}</div>;
    }

    const ksqlDB = ksqlDBData?.ksqlDB;

    if (!ksqlDB) {
        return <div>ksqlDB instance not found.</div>;
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div>
                <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
                    {ksqlDB.name}
                    <Badge variant={ksqlDB.isReachable ? "default" : "destructive"}>
                        {ksqlDB.isReachable ? "Online" : "Offline"}
                    </Badge>
                </h2>
                <p className="text-muted-foreground">
                    Details for ksqlDB instance <span className="font-mono">{ksqlDB.name}</span>
                </p>
            </div>

            {/* Info Cards */}
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
                        <CardTitle>Connection Status</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <Badge variant={ksqlDB.isReachable ? "default" : "destructive"}>
                            {ksqlDB.isReachable ? "Online" : "Offline"}
                        </Badge>
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

            {/* Main Content Tabs */}
            <Tabs defaultValue="query" className="w-full">
                <TabsList className="grid w-full grid-cols-3">
                    <TabsTrigger value="query" className="flex items-center gap-2">
                        <Code className="h-4 w-4" />
                        Query Editor
                    </TabsTrigger>
                    <TabsTrigger value="history" className="flex items-center gap-2">
                        <History className="h-4 w-4" />
                        Query History
                    </TabsTrigger>
                    <TabsTrigger value="streams-tables" className="flex items-center gap-2">
                        <Database className="h-4 w-4" />
                        Streams & Tables
                    </TabsTrigger>
                </TabsList>

                <TabsContent value="query" className="mt-6 space-y-0">
                    <div className="grid gap-4 grid-cols-1 lg:grid-cols-2 h-[calc(100vh-350px)] min-h-[700px]">
                        <div className="flex flex-col h-full min-h-0">
                            <KsqlQueryEditor
                                ksqlDBId={ksqlDBId!}
                                onExecute={handleExecuteQuery}
                                isExecuting={isExecuting}
                                onCancel={handleCancel}
                                className="h-full"
                            />
                        </div>
                        <div className="flex flex-col h-full min-h-0">
                            <KsqlQueryResultViewer
                                result={queryResult}
                                isLoading={isExecuting}
                                className="h-full"
                            />
                        </div>
                    </div>
                </TabsContent>

                <TabsContent value="history" className="mt-6">
                    {ksqlDBId && <KsqlQueryHistory ksqlDBId={ksqlDBId} />}
                </TabsContent>

                <TabsContent value="streams-tables" className="mt-6">
                    {ksqlDBId && <KsqlStreamsTablesManagement ksqlDBId={ksqlDBId} />}
                </TabsContent>
            </Tabs>
        </div>
    );
}

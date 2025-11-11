import {useParams} from 'react-router-dom';
import {
    GET_SCHEMA_REGISTRY,
    GET_SCHEMA_REGISTRY_LATEST_SCHEMA,
    GET_SCHEMA_REGISTRY_SCHEMA_VERSIONS,
    GET_SCHEMA_REGISTRY_SUBJECTS
} from '@/graphql/queries';
import type {
    GetSchemaRegistryLatestSchemaQuery,
    GetSchemaRegistryQuery,
    GetSchemaRegistrySchemaVersionsQuery,
    GetSchemaRegistrySubjectsQuery
} from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {executeGraphQL} from '@/lib/graphql-client';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {Badge} from '@/components/ui/badge';
import {formatRelativeTime} from '@/lib/formatters';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {useEffect, useState} from 'react';
import {toast} from "sonner";

export default function SchemaRegistryDetailPage() {
    const {srId} = useParams<{ clusterId: string; srId: string }>();

    const {
        data: schemaRegistryData,
        isLoading: schemaRegistryLoading,
        error: schemaRegistryError
    } = useGraphQLQuery<GetSchemaRegistryQuery, {id: string}>(GET_SCHEMA_REGISTRY, 
        srId ? {id: srId} : undefined,
        {
            enabled: !!srId,
        }
    );

    const [subjects, setSubjects] = useState<string[]>([]);
    const [selectedSubject, setSelectedSubject] = useState<string | null>(null);
    const [latestSchema, setLatestSchema] = useState<string | null>(null);
    const [schemaVersions, setSchemaVersions] = useState<number[]>([]);
    const [subjectsLoading, setSubjectsLoading] = useState(false);
    const [schemaLoading, setSchemaLoading] = useState(false);

    const schemaRegistry = schemaRegistryData?.schemaRegistry;

    useEffect(() => {
        if (schemaRegistry) {
            fetchSubjects();
        }
    }, [schemaRegistry]);

    useEffect(() => {
        if (selectedSubject && schemaRegistry) {
            fetchLatestSchema(selectedSubject);
            fetchSchemaVersions(selectedSubject);
        }
    }, [selectedSubject]);

    const fetchSubjects = async () => {
        setSubjectsLoading(true);
        try {
            const data = await executeGraphQL<GetSchemaRegistrySubjectsQuery>(GET_SCHEMA_REGISTRY_SUBJECTS, {
                schemaRegistryId: srId!,
            });
            if (data && data.schemaRegistrySubjects) {
                setSubjects(data.schemaRegistrySubjects);
                if (data.schemaRegistrySubjects.length > 0) {
                    setSelectedSubject(data.schemaRegistrySubjects[0]);
                }
            }
        } catch (err: any) {
            toast.error(err.message || "Failed to fetch subjects");
        } finally {
            setSubjectsLoading(false);
        }
    };

    const fetchLatestSchema = async (subject: string) => {
        setSchemaLoading(true);
        try {
            const data = await executeGraphQL<GetSchemaRegistryLatestSchemaQuery>(GET_SCHEMA_REGISTRY_LATEST_SCHEMA, {
                schemaRegistryId: srId!,
                subject,
            });
            if (data && data.schemaRegistryLatestSchema) {
                setLatestSchema(JSON.stringify(JSON.parse(data.schemaRegistryLatestSchema), null, 2));
            }
        } catch (err: any) {
            toast.error(err.message || "Failed to fetch latest schema");
            setLatestSchema("Error loading schema.");
        } finally {
            setSchemaLoading(false);
        }
    };

    const fetchSchemaVersions = async (subject: string) => {
        try {
            const data = await executeGraphQL<GetSchemaRegistrySchemaVersionsQuery>(GET_SCHEMA_REGISTRY_SCHEMA_VERSIONS, {
                schemaRegistryId: srId!,
                subject,
            });
            if (data && data.schemaRegistrySchemaVersions) {
                setSchemaVersions(data.schemaRegistrySchemaVersions);
            }
        } catch (err: any) {
            toast.error(err.message || "Failed to fetch schema versions");
            setSchemaVersions([]);
        }
    };

    if (schemaRegistryLoading) {
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

    if (schemaRegistryError) {
        return <div className="text-destructive">Error loading schema registry
            details: {schemaRegistryError.message}</div>;
    }

    if (!schemaRegistry) {
        return <div>Schema Registry not found.</div>;
    }

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
                    {schemaRegistry.name}
                    <Badge variant={schemaRegistry.isReachable ? "default" : "destructive"}>
                        {schemaRegistry.isReachable ? "Online" : "Offline"}
                    </Badge>
                </h2>
                <p className="text-muted-foreground">Details for Schema Registry <span
                    className="font-mono">{schemaRegistry.name}</span></p>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <Card>
                    <CardHeader>
                        <CardTitle>URL</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-sm font-mono break-all">{schemaRegistry.url}</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Status</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className={schemaRegistry.isActive ? 'text-green-400 font-medium' : 'text-gray-500'}>
                            {schemaRegistry.isActive ? 'Active' : 'Inactive'}
                        </p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Last Checked</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-sm">{formatRelativeTime(schemaRegistry.lastConnectionCheck)}</p>
                        {schemaRegistry.lastConnectionError && (
                            <p className="text-destructive text-xs mt-1">{schemaRegistry.lastConnectionError}</p>
                        )}
                    </CardContent>
                </Card>
            </div>

            <Card>
                <CardHeader>
                    <CardTitle>Subjects</CardTitle>
                    <CardDescription>Schemas registered in this Schema Registry.</CardDescription>
                </CardHeader>
                <CardContent>
                    {subjectsLoading ? (
                        <Skeleton className="h-32 w-full"/>
                    ) : subjects.length === 0 ? (
                        <p className="text-muted-foreground">No subjects found.</p>
                    ) : (
                        <Tabs value={selectedSubject || undefined} onValueChange={setSelectedSubject}
                              orientation="vertical">
                            <TabsList className="flex flex-col h-auto p-2">
                                {subjects.map(subject => (
                                    <TabsTrigger key={subject} value={subject} className="w-full text-left">
                                        {subject}
                                    </TabsTrigger>
                                ))}
                            </TabsList>
                            {subjects.map(subject => (
                                <TabsContent key={subject} value={subject} className="w-full">
                                    <Card>
                                        <CardHeader>
                                            <CardTitle>Schema for {subject}</CardTitle>
                                            <CardDescription>Latest version of the schema for this
                                                subject.</CardDescription>
                                        </CardHeader>
                                        <CardContent>
                                            {schemaLoading ? (
                                                <Skeleton className="h-64 w-full"/>
                                            ) : (
                                                <div className="border rounded-lg overflow-auto max-h-[600px] bg-muted/30">
                                                  <pre className="p-4 font-mono text-sm">
                                                    {latestSchema || '{}'}
                                                  </pre>
                                                </div>
                                            )}
                                            <div className="mt-4">
                                                <h4 className="font-semibold">Versions:</h4>
                                                {schemaVersions.length > 0 ? (
                                                    <div className="flex flex-wrap gap-2 mt-2">
                                                        {schemaVersions.map(version => (
                                                            <Badge key={version} variant="secondary">{version}</Badge>
                                                        ))}
                                                    </div>
                                                ) : (
                                                    <p className="text-muted-foreground text-sm">No versions found.</p>
                                                )}
                                            </div>
                                        </CardContent>
                                    </Card>
                                </TabsContent>
                            ))}
                        </Tabs>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}

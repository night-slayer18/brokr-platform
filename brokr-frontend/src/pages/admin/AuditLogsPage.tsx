import {useState} from 'react'
import {GET_AUDIT_LOGS, GET_AUDIT_LOG_STATISTICS} from '@/graphql/queries'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import type {
    AuditLog,
    GetAuditLogsQuery,
    GetAuditLogStatisticsQuery,
    GetAuditLogsVariables,
    GetAuditLogStatisticsVariables,
    AuditActionType,
    AuditResourceType,
    AuditStatus,
    AuditSeverity
} from '@/graphql/types'
import {
    AUDIT_ACTION_TYPES,
    AUDIT_RESOURCE_TYPES,
    AUDIT_STATUSES,
    AUDIT_SEVERITIES
} from '@/lib/constants'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Badge} from '@/components/ui/badge'
import {Skeleton} from '@/components/ui/skeleton'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {FileText, Search, Filter, Eye} from 'lucide-react'
import {format} from 'date-fns'
import {Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {ScrollArea} from '@/components/ui/scroll-area'

const ACTION_TYPES = AUDIT_ACTION_TYPES as readonly AuditActionType[]
const RESOURCE_TYPES = AUDIT_RESOURCE_TYPES as readonly AuditResourceType[]
const STATUSES = AUDIT_STATUSES as readonly AuditStatus[]
const SEVERITIES = AUDIT_SEVERITIES as readonly AuditSeverity[]

function getSeverityColor(severity: AuditSeverity) {
    switch (severity) {
        case 'CRITICAL':
            return 'bg-red-500/20 text-red-700 dark:text-red-400 border-red-500/50'
        case 'ERROR':
            return 'bg-orange-500/20 text-orange-700 dark:text-orange-400 border-orange-500/50'
        case 'WARNING':
            return 'bg-yellow-500/20 text-yellow-700 dark:text-yellow-400 border-yellow-500/50'
        default:
            return 'bg-blue-500/20 text-blue-700 dark:text-blue-400 border-blue-500/50'
    }
}

function getStatusColor(status: AuditStatus) {
    switch (status) {
        case 'SUCCESS':
            return 'bg-green-500/20 text-green-700 dark:text-green-400 border-green-500/50'
        case 'FAILURE':
            return 'bg-red-500/20 text-red-700 dark:text-red-400 border-red-500/50'
        default:
            return 'bg-yellow-500/20 text-yellow-700 dark:text-yellow-400 border-yellow-500/50'
    }
}

export default function AuditLogsPage() {
    const [page, setPage] = useState(0)
    const [pageSize] = useState(10)
    const [filters, setFilters] = useState({
        actionType: 'ALL',
        resourceType: 'ALL',
        status: 'ALL',
        severity: 'ALL',
        searchText: '',
    })
    const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null)
    const [detailDialogOpen, setDetailDialogOpen] = useState(false)

    // Build filter object for GraphQL
    const filterInput: GetAuditLogsVariables['filter'] = {}
    if (filters.actionType && filters.actionType !== 'ALL') filterInput.actionType = filters.actionType as AuditActionType
    if (filters.resourceType && filters.resourceType !== 'ALL') filterInput.resourceType = filters.resourceType as AuditResourceType
    if (filters.status && filters.status !== 'ALL') filterInput.status = filters.status as AuditStatus
    if (filters.severity && filters.severity !== 'ALL') filterInput.severity = filters.severity as AuditSeverity
    if (filters.searchText) filterInput.searchText = filters.searchText


    const {data, isLoading, error} = useGraphQLQuery<GetAuditLogsQuery, GetAuditLogsVariables>(
        GET_AUDIT_LOGS,
        {
            filter: Object.keys(filterInput).length > 0 ? filterInput : null,
            pagination: {
                page,
                size: pageSize,
                sortBy: 'timestamp',
                sortDirection: 'DESC',
            },
        },
        {
            placeholderData: (previousData) => previousData, // Keep previous data while fetching new page
        }
    )

    const {data: statistics} = useGraphQLQuery<GetAuditLogStatisticsQuery, GetAuditLogStatisticsVariables>(
        GET_AUDIT_LOG_STATISTICS,
        {filter: null}
    )

    const handleViewDetails = (log: AuditLog) => {
        setSelectedLog(log)
        setDetailDialogOpen(true)
    }

    const handleResetFilters = () => {
        setFilters({
            actionType: 'ALL',
            resourceType: 'ALL',
            status: 'ALL',
            severity: 'ALL',
            searchText: '',
        })
        setPage(0)
    }

    if (error) {
        return (
            <div className="flex items-center justify-center h-96">
                <div className="text-center">
                    <p className="text-destructive">Failed to load audit logs</p>
                    <p className="text-sm text-muted-foreground mt-2">{error.message}</p>
                </div>
            </div>
        )
    }

    const auditLogs = data?.auditLogs?.content || []
    const hasNext = data?.auditLogs?.hasNext || false
    const hasPrevious = data?.auditLogs?.hasPrevious || false

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                        Audit Logs
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        View and analyze all user actions and system events
                    </p>
                </div>
            </div>

            {/* Statistics Cards */}
            {statistics && (
                <div className="grid gap-4 md:grid-cols-4">
                    <Card>
                        <CardHeader className="pb-2">
                            <CardDescription>Total Events</CardDescription>
                            <CardTitle className="text-2xl">{statistics.auditLogStatistics.totalCount.toLocaleString()}</CardTitle>
                        </CardHeader>
                    </Card>
                    <Card>
                        <CardHeader className="pb-2">
                            <CardDescription>Failed Actions</CardDescription>
                            <CardTitle className="text-2xl text-destructive">
                                {statistics.auditLogStatistics.byStatus.find(s => s.status === 'FAILURE')?.count || 0}
                            </CardTitle>
                        </CardHeader>
                    </Card>
                    <Card>
                        <CardHeader className="pb-2">
                            <CardDescription>Critical Events</CardDescription>
                            <CardTitle className="text-2xl text-red-600">
                                {statistics.auditLogStatistics.bySeverity.find(s => s.severity === 'CRITICAL')?.count || 0}
                            </CardTitle>
                        </CardHeader>
                    </Card>
                    <Card>
                        <CardHeader className="pb-2">
                            <CardDescription>Recent Activity</CardDescription>
                            <CardTitle className="text-2xl">
                                {statistics.auditLogStatistics.recentActivity.length}
                            </CardTitle>
                        </CardHeader>
                    </Card>
                </div>
            )}

            {/* Filters */}
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Filter className="h-5 w-5"/>
                        Filters
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="grid gap-4 md:grid-cols-5">
                        <div className="space-y-2">
                            <Label>Search</Label>
                            <div className="relative">
                                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground"/>
                                <Input
                                    placeholder="Search..."
                                    value={filters.searchText}
                                    onChange={(e) => setFilters({...filters, searchText: e.target.value})}
                                    className="pl-8"
                                />
                            </div>
                        </div>
                        <div className="space-y-2">
                            <Label>Action Type</Label>
                            <Select
                                value={filters.actionType}
                                onValueChange={(value) => setFilters({...filters, actionType: value})}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="All actions"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="ALL">All actions</SelectItem>
                                    {ACTION_TYPES.map((type) => (
                                        <SelectItem key={type} value={type}>
                                            {type}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label>Resource Type</Label>
                            <Select
                                value={filters.resourceType}
                                onValueChange={(value) => setFilters({...filters, resourceType: value})}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="All resources"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="ALL">All resources</SelectItem>
                                    {RESOURCE_TYPES.map((type) => (
                                        <SelectItem key={type} value={type}>
                                            {type}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label>Status</Label>
                            <Select
                                value={filters.status}
                                onValueChange={(value) => setFilters({...filters, status: value})}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="All statuses"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="ALL">All statuses</SelectItem>
                                    {STATUSES.map((status) => (
                                        <SelectItem key={status} value={status}>
                                            {status}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label>Severity</Label>
                            <Select
                                value={filters.severity}
                                onValueChange={(value) => setFilters({...filters, severity: value})}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="All severities"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="ALL">All severities</SelectItem>
                                    {SEVERITIES.map((severity) => (
                                        <SelectItem key={severity} value={severity}>
                                            {severity}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                    </div>
                    <div className="flex gap-2 mt-4">
                        <Button variant="outline" onClick={handleResetFilters}>
                            Reset Filters
                        </Button>
                    </div>
                </CardContent>
            </Card>

            {/* Audit Logs Table */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <div>
                            <CardTitle>Audit Logs</CardTitle>
                        </div>
                    </div>
                </CardHeader>
                <CardContent>
                    {isLoading ? (
                        <div className="space-y-2">
                            {[1, 2, 3, 4, 5].map((i) => (
                                <Skeleton key={i} className="h-16 w-full"/>
                            ))}
                        </div>
                    ) : auditLogs.length === 0 ? (
                        <div className="text-center py-12">
                            <FileText className="h-12 w-12 text-muted-foreground mx-auto mb-4"/>
                            <p className="text-muted-foreground">No audit logs found</p>
                        </div>
                    ) : (
                        <>
                            <div className="rounded-md border">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Timestamp</TableHead>
                                            <TableHead>User</TableHead>
                                            <TableHead>Action</TableHead>
                                            <TableHead>Resource</TableHead>
                                            <TableHead>Status</TableHead>
                                            <TableHead>Severity</TableHead>
                                            <TableHead>IP Address</TableHead>
                                            <TableHead>Actions</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {auditLogs.map((log) => (
                                            <TableRow key={log.id}>
                                                <TableCell className="font-mono text-sm">
                                                    {format(new Date(log.timestamp), 'yyyy-MM-dd HH:mm:ss')}
                                                </TableCell>
                                                <TableCell>
                                                    <div>
                                                        <div className="font-medium">{log.userEmail || 'N/A'}</div>
                                                        {log.userRole && (
                                                            <div className="text-xs text-muted-foreground">{log.userRole}</div>
                                                        )}
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    <Badge variant="outline">{log.actionType}</Badge>
                                                </TableCell>
                                                <TableCell>
                                                    <div>
                                                        <div className="font-medium">{log.resourceType}</div>
                                                        {log.resourceName && (
                                                            <div className="text-xs text-muted-foreground">{log.resourceName}</div>
                                                        )}
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    <Badge className={getStatusColor(log.status)} variant="outline">
                                                        {log.status}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell>
                                                    <Badge className={getSeverityColor(log.severity)} variant="outline">
                                                        {log.severity}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell className="font-mono text-xs">
                                                    {log.ipAddress || 'N/A'}
                                                </TableCell>
                                                <TableCell>
                                                    <Button
                                                        variant="ghost"
                                                        size="sm"
                                                        onClick={() => handleViewDetails(log)}
                                                    >
                                                        <Eye className="h-4 w-4"/>
                                                    </Button>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </div>

                            {/* Pagination */}
                            <div className="flex items-center justify-between mt-4">
                                <div className="text-sm text-muted-foreground">
                                    Page {page + 1}
                                </div>
                                <div className="flex gap-2">
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setPage(Math.max(0, page - 1))}
                                        disabled={!hasPrevious}
                                    >
                                        Previous
                                    </Button>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setPage(page + 1)}
                                        disabled={!hasNext}
                                    >
                                        Next
                                    </Button>
                                </div>
                            </div>
                        </>
                    )}
                </CardContent>
            </Card>

            {/* Detail Dialog */}
            <Dialog open={detailDialogOpen} onOpenChange={setDetailDialogOpen}>
                <DialogContent className="max-w-4xl max-h-[90vh]">
                    <DialogHeader>
                        <DialogTitle>Audit Log Details</DialogTitle>
                        <DialogDescription>
                            Complete information about this audit event
                        </DialogDescription>
                    </DialogHeader>
                    {selectedLog && (
                        <ScrollArea className="max-h-[70vh] pr-4">
                            <div className="space-y-4">
                                <div className="grid gap-4 md:grid-cols-2">
                                    <div>
                                        <Label className="text-xs text-muted-foreground">ID</Label>
                                        <p className="font-mono text-sm">{selectedLog.id}</p>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Timestamp</Label>
                                        <p className="text-sm">
                                            {format(new Date(selectedLog.timestamp), 'yyyy-MM-dd HH:mm:ss.SSS')}
                                        </p>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">User</Label>
                                        <p className="text-sm">{selectedLog.userEmail || 'N/A'}</p>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">User Role</Label>
                                        <p className="text-sm">{selectedLog.userRole || 'N/A'}</p>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Action Type</Label>
                                        <Badge variant="outline">{selectedLog.actionType}</Badge>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Resource Type</Label>
                                        <Badge variant="outline">{selectedLog.resourceType}</Badge>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Resource ID</Label>
                                        <p className="font-mono text-sm">{selectedLog.resourceId || 'N/A'}</p>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Resource Name</Label>
                                        <p className="text-sm">{selectedLog.resourceName || 'N/A'}</p>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Status</Label>
                                        <Badge className={getStatusColor(selectedLog.status)} variant="outline">
                                            {selectedLog.status}
                                        </Badge>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Severity</Label>
                                        <Badge className={getSeverityColor(selectedLog.severity)} variant="outline">
                                            {selectedLog.severity}
                                        </Badge>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">IP Address</Label>
                                        <p className="font-mono text-sm">{selectedLog.ipAddress || 'N/A'}</p>
                                    </div>
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Request ID</Label>
                                        <p className="font-mono text-sm">{selectedLog.requestId || 'N/A'}</p>
                                    </div>
                                </div>

                                {selectedLog.errorMessage && (
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Error Message</Label>
                                        <p className="text-sm text-destructive bg-destructive/10 p-2 rounded">
                                            {selectedLog.errorMessage}
                                        </p>
                                    </div>
                                )}

                                {selectedLog.changedFields && selectedLog.changedFields.length > 0 && (
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Changed Fields</Label>
                                        <div className="flex flex-wrap gap-2 mt-1">
                                            {selectedLog.changedFields.map((field) => (
                                                <Badge key={field} variant="outline">{field}</Badge>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {selectedLog.oldValues && Object.keys(selectedLog.oldValues).length > 0 && (
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Previous State</Label>
                                        <div className="mt-1 rounded-md border bg-muted/50 p-3">
                                            <pre className="text-xs font-mono overflow-auto max-h-48">
                                                {JSON.stringify(selectedLog.oldValues, null, 2)}
                                            </pre>
                                        </div>
                                    </div>
                                )}

                                {selectedLog.newValues && Object.keys(selectedLog.newValues).length > 0 && (
                                    <div>
                                        <Label className="text-xs text-muted-foreground">New State</Label>
                                        <div className="mt-1 rounded-md border bg-muted/50 p-3">
                                            <pre className="text-xs font-mono overflow-auto max-h-48">
                                                {JSON.stringify(selectedLog.newValues, null, 2)}
                                            </pre>
                                        </div>
                                    </div>
                                )}

                                {selectedLog.metadata && Object.keys(selectedLog.metadata).length > 0 && (
                                    <div>
                                        <Label className="text-xs text-muted-foreground">Metadata</Label>
                                        <div className="mt-1 rounded-md border bg-muted/50 p-3">
                                            <pre className="text-xs font-mono overflow-auto max-h-48">
                                                {JSON.stringify(selectedLog.metadata, null, 2)}
                                            </pre>
                                        </div>
                                    </div>
                                )}
                            </div>
                        </ScrollArea>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    )
}


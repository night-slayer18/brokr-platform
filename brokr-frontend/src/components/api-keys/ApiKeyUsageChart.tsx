import {useState, useEffect} from 'react'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {GET_API_KEY_USAGE} from '@/graphql/queries'
import type {GetApiKeyUsageQuery, GetApiKeyUsageQueryVariables} from '@/graphql/types'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {TimeRangeSelector} from '@/components/metrics/TimeRangeSelector'
import {BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer} from 'recharts'
import {subDays} from 'date-fns'
import {Loader2} from 'lucide-react'

interface ApiKeyUsageChartProps {
    apiKeyId: string
    apiKeyName?: string
}

export function ApiKeyUsageChart({apiKeyId, apiKeyName}: ApiKeyUsageChartProps) {
    const defaultStartTime = subDays(new Date(), 7).getTime()
    const defaultEndTime = new Date().getTime()
    
    const [timeRange, setTimeRange] = useState({
        startTime: defaultStartTime,
        endTime: defaultEndTime,
    })
    
    const {data, isLoading, refetch} = useGraphQLQuery<
        GetApiKeyUsageQuery,
        GetApiKeyUsageQueryVariables
    >(
        GET_API_KEY_USAGE,
        {
            id: apiKeyId,
            startTime: new Date(timeRange.startTime).toISOString(),
            endTime: new Date(timeRange.endTime).toISOString(),
        },
        {
            enabled: !!apiKeyId,
        }
    )
    
    useEffect(() => {
        if (apiKeyId) {
            refetch()
        }
    }, [timeRange, apiKeyId, refetch])
    
    const handleTimeRangeChange = (newTimeRange: { startTime: number; endTime: number }) => {
        setTimeRange(newTimeRange)
    }
    
    const usage = data?.apiKeyUsage
    
    if (isLoading) {
        return (
            <Card>
                <CardHeader>
                    <CardTitle>Usage Statistics</CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="flex items-center justify-center h-64">
                        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground"/>
                    </div>
                </CardContent>
            </Card>
        )
    }
    
    if (!usage) {
        return (
            <Card>
                <CardHeader>
                    <CardTitle>Usage Statistics</CardTitle>
                </CardHeader>
                <CardContent>
                    <p className="text-muted-foreground">No usage data available</p>
                </CardContent>
            </Card>
        )
    }
    
    const statusCodeData = usage.statusCodeCounts
        ? Object.entries(usage.statusCodeCounts).map(([code, count]) => ({
              code: code.toString(),
              count: Number(count),
          }))
        : []
    
    return (
        <div className="space-y-6">
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <div>
                            <CardTitle>Usage Statistics</CardTitle>
                            <CardDescription>
                                {apiKeyName ? `Usage metrics for "${apiKeyName}"` : 'API key usage metrics and analytics'}
                            </CardDescription>
                        </div>
                        <TimeRangeSelector
                            onTimeRangeChange={handleTimeRangeChange}
                            defaultRange={timeRange}
                        />
                    </div>
                </CardHeader>
                <CardContent className="space-y-6">
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                        <div className="space-y-1">
                            <p className="text-sm text-muted-foreground">Total Requests</p>
                            <p className="text-2xl font-bold">{usage.totalRequests.toLocaleString()}</p>
                        </div>
                        <div className="space-y-1">
                            <p className="text-sm text-muted-foreground">Success</p>
                            <p className="text-2xl font-bold text-green-600">
                                {usage.successCount.toLocaleString()}
                            </p>
                        </div>
                        <div className="space-y-1">
                            <p className="text-sm text-muted-foreground">Errors</p>
                            <p className="text-2xl font-bold text-red-600">
                                {usage.errorCount.toLocaleString()}
                            </p>
                        </div>
                        <div className="space-y-1">
                            <p className="text-sm text-muted-foreground">Error Rate</p>
                            <p className="text-2xl font-bold">
                                {(usage.errorRate * 100).toFixed(2)}%
                            </p>
                        </div>
                    </div>
                    
                    {usage.averageResponseTimeMs && (
                        <div className="space-y-1">
                            <p className="text-sm text-muted-foreground">Average Response Time</p>
                            <p className="text-2xl font-bold">{usage.averageResponseTimeMs}ms</p>
                        </div>
                    )}
                    
                    {statusCodeData.length > 0 && (
                        <div className="space-y-4">
                            <h3 className="text-lg font-semibold">Status Code Distribution</h3>
                            <ResponsiveContainer width="100%" height={300}>
                                <BarChart data={statusCodeData}>
                                    <CartesianGrid strokeDasharray="3 3"/>
                                    <XAxis dataKey="code"/>
                                    <YAxis/>
                                    <Tooltip/>
                                    <Legend/>
                                    <Bar dataKey="count" fill="hsl(var(--primary))"/>
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    )
}


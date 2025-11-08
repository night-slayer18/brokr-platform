import {Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle} from "@/components/ui/sheet";
import {formatDate, formatRelativeTime} from "@/lib/formatters";
import {formatNumber} from "@/lib/utils";
import type {Message} from "@/types";
import {Badge} from "@/components/ui/badge";
import {Tabs, TabsContent, TabsList, TabsTrigger} from "@/components/ui/tabs";
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from "@/components/ui/table";

interface MessageDetailPanelProps {
    message: Message | null;
    isOpen: boolean;
    onOpenChange: (open: boolean) => void;
}

export function MessageDetailPanel({message, isOpen, onOpenChange}: MessageDetailPanelProps) {
    if (!message) return null;

    const tryParseJSON = (value: string | null | undefined) => {
        if (!value) return null;
        try {
            return JSON.parse(value);
        } catch {
            return value;
        }
    };

    const formatValue = (value: string | null | undefined) => {
        if (!value) return null;
        const parsed = tryParseJSON(value);
        return typeof parsed === 'object' ? JSON.stringify(parsed, null, 2) : value;
    };

    return (
        <Sheet open={isOpen} onOpenChange={onOpenChange}>
            <SheetContent
                className="!w-[calc(100vw-16rem)] !max-w-none !right-0 p-0 flex flex-col overflow-hidden">
                {/* Header */}
                <div
                    className="p-6 pb-4 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
                    <SheetHeader className="space-y-3">
                        <SheetTitle className="text-2xl font-bold">Message Details</SheetTitle>
                        <SheetDescription className="text-sm flex items-center gap-2 flex-wrap">
                            <span>Viewing message from</span>
                            <Badge variant="secondary" className="font-mono">
                                Partition {message.partition}
                            </Badge>
                            <span>at offset</span>
                            <Badge variant="secondary" className="font-mono">
                                {formatNumber(message.offset)}
                            </Badge>
                        </SheetDescription>
                    </SheetHeader>
                </div>

                {/* Scrollable Content with Tabs */}
                <div className="flex-1 overflow-y-auto">
                    <Tabs defaultValue="table" className="h-full flex flex-col">
                        <div className="px-6 pt-4 pb-2 border-b bg-background sticky top-0 z-10">
                            <TabsList className="grid w-full grid-cols-2 max-w-md">
                                <TabsTrigger value="table">Table View</TabsTrigger>
                                <TabsTrigger value="json">JSON View</TabsTrigger>
                            </TabsList>
                        </div>

                        {/* Table View */}
                        <TabsContent value="table" className="flex-1 p-6 mt-0 space-y-6">
                            {/* Metadata Section */}
                            <section className="space-y-3">
                                <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                                    <span className="h-5 w-1 bg-primary rounded-full"></span>
                                    Metadata
                                </h3>
                                <div className="border rounded-lg overflow-hidden">
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead className="w-1/3 font-semibold h-10">Property</TableHead>
                                                <TableHead className="font-semibold h-10">Value</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            <TableRow className="h-12">
                                                <TableCell className="font-medium py-2">Partition</TableCell>
                                                <TableCell
                                                    className="font-mono text-base font-semibold py-2">{message.partition}</TableCell>
                                            </TableRow>
                                            <TableRow className="h-12">
                                                <TableCell className="font-medium py-2">Offset</TableCell>
                                                <TableCell
                                                    className="font-mono text-base font-semibold py-2">{formatNumber(message.offset)}</TableCell>
                                            </TableRow>
                                            <TableRow className="h-14">
                                                <TableCell className="font-medium py-2">Timestamp</TableCell>
                                                <TableCell className="py-2">
                                                    <div className="space-y-0.5">
                                                        <p className="font-semibold text-sm">{formatDate(message.timestamp)}</p>
                                                        <p className="text-xs text-muted-foreground">{formatRelativeTime(message.timestamp)}</p>
                                                    </div>
                                                </TableCell>
                                            </TableRow>
                                            <TableRow className="h-12">
                                                <TableCell className="font-medium py-2">Timestamp (ISO)</TableCell>
                                                <TableCell
                                                    className="font-mono text-xs py-2">{new Date(message.timestamp).toISOString()}</TableCell>
                                            </TableRow>
                                        </TableBody>
                                    </Table>
                                </div>
                            </section>

                            {/* Key Section */}
                            <section className="space-y-3">
                                <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                                    <span className="h-5 w-1 bg-primary rounded-full"></span>
                                    Key
                                </h3>
                                <div className="border rounded-lg overflow-hidden">
                                    <Table>
                                        <TableBody>
                                            <TableRow>
                                                <TableCell className="p-0">
                                                    {message.key ? (
                                                        <pre
                                                            className="p-4 text-sm font-mono whitespace-pre-wrap break-words overflow-x-auto bg-muted/30">
{formatValue(message.key)}
                                                        </pre>
                                                    ) : (
                                                        <div className="p-4 text-center">
                                                            <p className="text-sm text-muted-foreground italic">(null)</p>
                                                        </div>
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        </TableBody>
                                    </Table>
                                </div>
                            </section>

                            {/* Value Section */}
                            <section className="space-y-3">
                                <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                                    <span className="h-5 w-1 bg-primary rounded-full"></span>
                                    Value
                                </h3>
                                <div className="border rounded-lg overflow-hidden">
                                    <Table>
                                        <TableBody>
                                            <TableRow>
                                                <TableCell className="p-0">
                                                    {message.value ? (
                                                        <pre
                                                            className="p-4 text-sm font-mono whitespace-pre-wrap break-words overflow-x-auto bg-muted/30">
{formatValue(message.value)}
                                                        </pre>
                                                    ) : (
                                                        <div className="p-4 text-center">
                                                            <p className="text-sm text-muted-foreground italic">(null)</p>
                                                        </div>
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        </TableBody>
                                    </Table>
                                </div>
                            </section>
                        </TabsContent>

                        {/* JSON View */}
                        <TabsContent value="json" className="flex-1 p-6 mt-0">
                            <div className="border rounded-lg overflow-hidden">
                                <div className="bg-slate-950 text-green-400">
                                    <pre
                                        className="p-6 text-sm font-mono whitespace-pre-wrap break-words overflow-x-auto">
{JSON.stringify({
    partition: message.partition,
    offset: message.offset,
    timestamp: message.timestamp,
    timestampISO: new Date(message.timestamp).toISOString(),
    timestampFormatted: formatDate(message.timestamp),
    key: tryParseJSON(message.key),
    value: tryParseJSON(message.value)
}, null, 2)}
                                    </pre>
                                </div>
                            </div>
                        </TabsContent>
                    </Tabs>
                </div>
            </SheetContent>
        </Sheet>
    );
}


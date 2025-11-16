import {useState} from 'react'
import {Button} from '@/components/ui/button'
import {Copy, Check, Download} from 'lucide-react'
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog'
import {Alert, AlertDescription} from '@/components/ui/alert'
import {toast} from 'sonner'

interface BackupCodesDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    backupCodes: string[]
    title?: string
    description?: string
}

export function BackupCodesDialog({
    open,
    onOpenChange,
    backupCodes,
    title = 'Backup Codes',
    description = 'Save these backup codes in a secure location. You can use them to access your account if you lose your authenticator device. Each code can only be used once.',
}: BackupCodesDialogProps) {
    const [copiedIndex, setCopiedIndex] = useState<number | null>(null)

    const handleCopyCode = (code: string, index: number) => {
        navigator.clipboard.writeText(code)
        setCopiedIndex(index)
        setTimeout(() => setCopiedIndex(null), 2000)
        toast.success('Backup code copied to clipboard')
    }

    const handleDownload = () => {
        const codesText = backupCodes.join('\n')
        const blob = new Blob([codesText], {type: 'text/plain'})
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = 'brokr-backup-codes.txt'
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
        toast.success('Backup codes downloaded')
    }

    const handleCopyAll = () => {
        const codesText = backupCodes.join('\n')
        navigator.clipboard.writeText(codesText)
        toast.success('All backup codes copied to clipboard')
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>{title}</DialogTitle>
                    <DialogDescription>{description}</DialogDescription>
                </DialogHeader>

                <Alert>
                    <AlertDescription>
                        <strong>Important:</strong> Save these backup codes in a secure location. You can use them to
                        access your account if you lose your authenticator device. Each code can only be used once.
                    </AlertDescription>
                </Alert>

                <div className="flex gap-2 mb-4">
                    <Button variant="outline" onClick={handleCopyAll} className="flex-1">
                        <Copy className="mr-2 h-4 w-4"/>
                        Copy All Codes
                    </Button>
                    <Button variant="outline" onClick={handleDownload} className="flex-1">
                        <Download className="mr-2 h-4 w-4"/>
                        Download as TXT
                    </Button>
                </div>

                <div className="grid grid-cols-2 gap-2 max-h-96 overflow-y-auto">
                    {backupCodes.map((code, index) => (
                        <div
                            key={index}
                            className="flex items-center justify-between p-3 bg-secondary rounded-md border"
                        >
                            <code className="font-mono text-sm">{code}</code>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => handleCopyCode(code, index)}
                            >
                                {copiedIndex === index ? (
                                    <Check className="h-4 w-4 text-green-500"/>
                                ) : (
                                    <Copy className="h-4 w-4"/>
                                )}
                            </Button>
                        </div>
                    ))}
                </div>

                <div className="flex justify-end gap-2 mt-4">
                    <Button onClick={() => onOpenChange(false)}>
                        I've Saved My Backup Codes
                    </Button>
                </div>
            </DialogContent>
        </Dialog>
    )
}


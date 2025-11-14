import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Clock } from 'lucide-react';
import { subHours, subDays, startOfDay, endOfDay } from 'date-fns';

interface TimeRangeSelectorProps {
  onTimeRangeChange: (timeRange: { startTime: number; endTime: number }) => void;
  defaultRange?: { startTime: number; endTime: number };
}

const PRESET_RANGES = {
  '1h': () => ({ startTime: subHours(new Date(), 1).getTime(), endTime: Date.now() }),
  '6h': () => ({ startTime: subHours(new Date(), 6).getTime(), endTime: Date.now() }),
  '24h': () => ({ startTime: subHours(new Date(), 24).getTime(), endTime: Date.now() }),
  '7d': () => ({ startTime: subDays(new Date(), 7).getTime(), endTime: Date.now() }),
  '30d': () => ({ startTime: subDays(new Date(), 30).getTime(), endTime: Date.now() }),
  'today': () => ({ startTime: startOfDay(new Date()).getTime(), endTime: endOfDay(new Date()).getTime() }),
};

export function TimeRangeSelector({ onTimeRangeChange }: TimeRangeSelectorProps) {
  const [selectedPreset, setSelectedPreset] = useState<string | null>(null);

  const handlePresetClick = (preset: keyof typeof PRESET_RANGES) => {
    const range = PRESET_RANGES[preset]();
    setSelectedPreset(preset);
    onTimeRangeChange(range);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Clock className="h-5 w-5" />
          Time Range
        </CardTitle>
        <CardDescription>Select a time range for metrics visualization</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="flex flex-wrap gap-2">
          <Button
            variant={selectedPreset === '1h' ? 'default' : 'outline'}
            size="sm"
            onClick={() => handlePresetClick('1h')}
          >
            Last Hour
          </Button>
          <Button
            variant={selectedPreset === '6h' ? 'default' : 'outline'}
            size="sm"
            onClick={() => handlePresetClick('6h')}
          >
            Last 6 Hours
          </Button>
          <Button
            variant={selectedPreset === '24h' ? 'default' : 'outline'}
            size="sm"
            onClick={() => handlePresetClick('24h')}
          >
            Last 24 Hours
          </Button>
          <Button
            variant={selectedPreset === '7d' ? 'default' : 'outline'}
            size="sm"
            onClick={() => handlePresetClick('7d')}
          >
            Last 7 Days
          </Button>
          <Button
            variant={selectedPreset === '30d' ? 'default' : 'outline'}
            size="sm"
            onClick={() => handlePresetClick('30d')}
          >
            Last 30 Days
          </Button>
          <Button
            variant={selectedPreset === 'today' ? 'default' : 'outline'}
            size="sm"
            onClick={() => handlePresetClick('today')}
          >
            Today
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}


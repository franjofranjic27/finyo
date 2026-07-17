import { useTranslation } from 'react-i18next';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CalculatorTab } from './CalculatorTab';
import { CompareTab } from './CompareTab';
import { ScenariosTab } from './ScenariosTab';

export function Pillar3Page() {
  const { t } = useTranslation();

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('nav.pillar3')}</h1>
      <Tabs defaultValue="calculator">
        {/* On narrow screens the tab pills scroll horizontally instead of wrapping. */}
        <div className="overflow-x-auto">
          <TabsList>
            <TabsTrigger value="calculator">{t('pillar3.tabs.calculator')}</TabsTrigger>
            <TabsTrigger value="compare">{t('pillar3.tabs.compare')}</TabsTrigger>
            <TabsTrigger value="scenarios">{t('pillar3.tabs.scenarios')}</TabsTrigger>
          </TabsList>
        </div>
        <TabsContent value="calculator" className="mt-4">
          <CalculatorTab />
        </TabsContent>
        <TabsContent value="compare" className="mt-4">
          <CompareTab />
        </TabsContent>
        <TabsContent value="scenarios" className="mt-4">
          <ScenariosTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

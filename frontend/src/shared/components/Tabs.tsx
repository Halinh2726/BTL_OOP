interface Tab<T extends string> {
  id: T;
  label: string;
}

interface TabsProps<T extends string> {
  tabs: Tab<T>[];
  active: T;
  onChange: (id: T) => void;
  size?: 'md' | 'lg';
}

export function Tabs<T extends string>({ tabs, active, onChange, size = 'md' }: TabsProps<T>) {
  const buttonSize = size === 'lg'
    ? 'min-h-11 min-w-11 flex-auto whitespace-nowrap px-1.5 py-2 text-xs sm:flex-1 sm:px-4 sm:py-4 sm:text-xl'
    : 'flex-1 px-4 py-2.5 text-sm';
  return (
    <div role="tablist" className="flex gap-1 rounded-xl bg-slate-200 p-1">
      {tabs.map((tab) => {
        const selected = tab.id === active;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(tab.id)}
            className={`rounded-lg font-semibold transition-colors ${buttonSize} ${
              selected ? 'bg-white text-blue-800 shadow-sm' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

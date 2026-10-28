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
  const text = size === 'lg' ? 'py-4 text-xl' : 'py-2.5 text-sm';
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
            className={`flex-1 rounded-lg px-4 font-semibold transition-colors ${text} ${
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

import { Search, X } from "lucide-react";
import { useEffect, useId, useState, type ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { cn } from "@/lib/utils/cn";

export function FilterBar({ children, onReset, canReset }: { children: ReactNode; onReset?: () => void; canReset?: boolean }) {
  return (
    <div className="flex flex-col gap-2 border-b p-3 sm:flex-row sm:flex-wrap sm:items-center">
      {children}
      {onReset && canReset ? (
        <Button variant="ghost" size="sm" onClick={onReset} className="text-muted-foreground sm:ml-auto">
          <X aria-hidden /> Clear filters
        </Button>
      ) : null}
    </div>
  );
}

const ALL = "__all__";

interface FilterSelectProps {
  label: string;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
  options: { value: string; label: string }[];
  allLabel: string;
  className?: string;
}

/** A labelled select whose first option clears the filter. */
export function FilterSelect({ label, value, onChange, options, allLabel, className }: FilterSelectProps) {
  return (
    <Select value={value ?? ALL} onValueChange={(next) => onChange(next === ALL ? undefined : next)}>
      <SelectTrigger size="sm" className={cn("h-8 w-full sm:w-44", className)} aria-label={label}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>{allLabel}</SelectItem>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

interface SearchInputProps {
  value: string | undefined;
  onChange: (value: string | undefined) => void;
  placeholder: string;
  label: string;
}

/** Debounced so typing doesn't send a request per keystroke. */
export function SearchInput({ value, onChange, placeholder, label }: SearchInputProps) {
  const id = useId();
  const [text, setText] = useState(value ?? "");
  const debounced = useDebouncedValue(text, 350);

  useEffect(() => {
    const next = debounced.trim() || undefined;
    if (next !== value) onChange(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to the debounced text
  }, [debounced]);

  // Follow external resets ("Clear filters"): adjust state during render, not in an effect.
  const [previous, setPrevious] = useState(value);
  if (value !== previous) {
    setPrevious(value);
    if (!value) setText("");
  }

  return (
    <div className="relative w-full sm:w-72">
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <Search className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <Input
        id={id}
        type="search"
        value={text}
        maxLength={100}
        onChange={(event) => setText(event.target.value)}
        placeholder={placeholder}
        className="h-8 pl-8"
      />
    </div>
  );
}

import type {
  InputHTMLAttributes,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react";

interface BaseFieldProps {
  label?: string;
  error?: string;
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement>, BaseFieldProps {}

export function Input({ label, error, className = "", id, ...rest }: InputProps) {
  const fieldId = id ?? rest.name ?? label?.toLowerCase().replace(/\s+/g, "-");
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={fieldId} className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
          {label}
        </label>
      )}
      <input
        id={fieldId}
        className={`rounded-lg border bg-white px-3.5 py-2.5 text-sm text-zinc-900 outline-none transition-colors placeholder:text-zinc-400 focus:ring-2 focus:ring-zinc-900/10 dark:bg-zinc-900 dark:text-zinc-100 dark:[color-scheme:dark] ${
          error
            ? "border-red-400 focus:border-red-500 focus:ring-red-500/10"
            : "border-zinc-300 focus:border-zinc-900 dark:border-zinc-700 dark:focus:border-zinc-400"
        } ${className}`}
        {...rest}
      />
      {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
    </div>
  );
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement>, BaseFieldProps {}

export function Select({ label, error, className = "", id, children, ...rest }: SelectProps) {
  const fieldId = id ?? rest.name ?? label?.toLowerCase().replace(/\s+/g, "-");
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={fieldId} className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
          {label}
        </label>
      )}
      <select
        id={fieldId}
        className={`rounded-lg border bg-white px-3.5 py-2.5 text-sm text-zinc-900 outline-none transition-colors dark:bg-zinc-900 dark:text-zinc-100 ${
          error
            ? "border-red-400 focus:border-red-500"
            : "border-zinc-300 focus:border-zinc-900 dark:border-zinc-700 dark:focus:border-zinc-400"
        } ${className}`}
        {...rest}
      >
        {children}
      </select>
      {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
    </div>
  );
}

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement>, BaseFieldProps {}

export function Textarea({ label, error, className = "", id, ...rest }: TextareaProps) {
  const fieldId = id ?? rest.name ?? label?.toLowerCase().replace(/\s+/g, "-");
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={fieldId} className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
          {label}
        </label>
      )}
      <textarea
        id={fieldId}
        className={`min-h-24 rounded-lg border bg-white px-3.5 py-2.5 text-sm text-zinc-900 outline-none transition-colors placeholder:text-zinc-400 focus:ring-2 focus:ring-zinc-900/10 dark:bg-zinc-900 dark:text-zinc-100 ${
          error
            ? "border-red-400 focus:border-red-500"
            : "border-zinc-300 focus:border-zinc-900 dark:border-zinc-700 dark:focus:border-zinc-400"
        } ${className}`}
        {...rest}
      />
      {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
    </div>
  );
}
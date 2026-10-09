export const MAX_LINE_QTY = 99;

export default function QuantityStepper({ value, onChange, min = 1, max = MAX_LINE_QTY, disabled, label = 'Quantity' }) {
  const clamp = (n) => Math.max(min, Math.min(max, n));
  return (
    <div className="stepper" role="group" aria-label={label}>
      <button type="button" onClick={() => onChange(clamp(value - 1))} disabled={disabled || value <= min} aria-label="Decrease quantity">−</button>
      <input
        type="number"
        min={min}
        max={max}
        value={value}
        disabled={disabled}
        aria-label={label}
        onChange={(e) => {
          const n = parseInt(e.target.value, 10);
          if (!Number.isNaN(n)) onChange(clamp(n));
        }}
      />
      <button type="button" onClick={() => onChange(clamp(value + 1))} disabled={disabled || value >= max} aria-label="Increase quantity">+</button>
    </div>
  );
}


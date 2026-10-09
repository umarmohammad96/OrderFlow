export default function FormField({ label, error, hint, id, children }) {
  return (
    <div className={`field ${error ? 'has-error' : ''}`}>
      {label && <label htmlFor={id}>{label}</label>}
      {children}
      {error ? (
        <div className="field-error" id={`${id}-error`}>{error}</div>
      ) : (
        hint && <div className="field-hint">{hint}</div>
      )}
    </div>
  );
}

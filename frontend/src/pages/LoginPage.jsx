import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import FormField from '../components/FormField.jsx';
import { ErrorBanner } from '../components/Feedback.jsx';
import { useAuth } from '../context/AuthContext.jsx';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Only allow same-app relative redirects. */
export function safeNext(next) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
}

export default function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const next = safeNext(params.get('next'));
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (isAuthenticated && !busy) return <Navigate to={next} replace />;

  const validate = () => {
    const e = {};
    if (!EMAIL_RE.test(form.email.trim())) e.email = 'Enter a valid email address';
    if (!form.password) e.password = 'Password is required';
    return e;
  };

  const onSubmit = async (ev) => {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    setServerError(null);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      await login(form.email.trim(), form.password);
      navigate(next, { replace: true });
    } catch (err) {
      if (err.code === 'VALIDATION_FAILED') setErrors(err.fieldErrors());
      setServerError(err);
      setBusy(false);
    }
  };

  const set = (k) => (ev) => setForm((f) => ({ ...f, [k]: ev.target.value }));

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={onSubmit} noValidate>
        <h1>Sign in</h1>
        <p className="muted">Welcome back to OrderFlow.</p>
        <ErrorBanner error={serverError} />
        <FormField label="Email" id="email" error={errors.email}>
          <input id="email" type="email" autoComplete="email" value={form.email} onChange={set('email')} aria-invalid={!!errors.email} />
        </FormField>
        <FormField label="Password" id="password" error={errors.password}>
          <input id="password" type="password" autoComplete="current-password" value={form.password} onChange={set('password')} aria-invalid={!!errors.password} />
        </FormField>
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="muted small center">
          New here? <Link to={`/register${next !== '/' ? `?next=${encodeURIComponent(next)}` : ''}`}>Create an account</Link>
        </p>
      </form>
    </div>
  );
}

import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import FormField from '../components/FormField.jsx';
import { ErrorBanner } from '../components/Feedback.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { safeNext } from './LoginPage.jsx';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function RegisterPage() {
  const { register, isAuthenticated } = useAuth();
  const toast = useToast();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const next = safeNext(params.get('next'));
  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (isAuthenticated && !busy) return <Navigate to={next} replace />;

  const validate = () => {
    const e = {};
    if (!form.name.trim()) e.name = 'Name is required';
    else if (form.name.trim().length > 100) e.name = 'Name is too long';
    if (!EMAIL_RE.test(form.email.trim())) e.email = 'Enter a valid email address';
    if (form.password.length < 8 || form.password.length > 72) e.password = 'Password must be 8–72 characters';
    if (form.confirm !== form.password) e.confirm = 'Passwords do not match';
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
      const user = await register(form.name.trim(), form.email.trim(), form.password);
      toast.success(`Welcome, ${user?.name || 'there'}!`);
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
        <h1>Create account</h1>
        <p className="muted">Shop the catalog and track orders end to end.</p>
        <ErrorBanner error={serverError} />
        <FormField label="Full name" id="name" error={errors.name}>
          <input id="name" autoComplete="name" value={form.name} onChange={set('name')} aria-invalid={!!errors.name} />
        </FormField>
        <FormField label="Email" id="email" error={errors.email}>
          <input id="email" type="email" autoComplete="email" value={form.email} onChange={set('email')} aria-invalid={!!errors.email} />
        </FormField>
        <FormField label="Password" id="password" error={errors.password} hint="8–72 characters">
          <input id="password" type="password" autoComplete="new-password" value={form.password} onChange={set('password')} aria-invalid={!!errors.password} />
        </FormField>
        <FormField label="Confirm password" id="confirm" error={errors.confirm}>
          <input id="confirm" type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} aria-invalid={!!errors.confirm} />
        </FormField>
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
        <p className="muted small center">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </div>
  );
}

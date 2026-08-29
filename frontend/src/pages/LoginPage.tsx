import { useState, type FormEvent, type ReactElement } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

import { useAuth } from '../features/auth/useAuth';
import type { ApiError } from '../types/auth';

type FormState = {
  email: string;
  password: string;
};

type FormErrors = Partial<Record<'email' | 'password' | 'form', string>>;

const initialState: FormState = {
  email: '',
  password: '',
};

export function LoginPage(): ReactElement {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [form, setForm] = useState<FormState>(initialState);
  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validate = (): FormErrors => {
    const nextErrors: FormErrors = {};

    if (!form.email.trim()) {
      nextErrors.email = 'Email is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      nextErrors.email = 'Enter a valid email address.';
    }

    if (!form.password) {
      nextErrors.password = 'Password is required.';
    }

    return nextErrors;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();

    const nextErrors = validate();
    setErrors(nextErrors);

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setIsSubmitting(true);

    try {
      await login({
        email: form.email.trim(),
        password: form.password,
      });

      const redirectTarget = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/dashboard';
      navigate(redirectTarget, { replace: true });
    } catch (error) {
      const apiError = error as ApiError;
      const fieldErrors = apiError.fieldErrors ?? {};
      const nextFormErrors: FormErrors = {
        form: fieldErrors.email ?? fieldErrors.password ?? apiError.message ?? 'Unable to sign in right now.',
      };

      if (fieldErrors.email) {
        nextFormErrors.email = fieldErrors.email;
      }

      if (fieldErrors.password) {
        nextFormErrors.password = fieldErrors.password;
      }

      setErrors(nextFormErrors);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header">
          <p className="eyebrow">OpsPilot</p>
          <h1>Welcome back</h1>
          <p className="subtitle">Sign in to continue to your workspace.</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <label className="field">
            <span>Email</span>
            <input
              type="email"
              name="email"
              value={form.email}
              onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
              autoComplete="email"
              aria-invalid={Boolean(errors.email)}
              aria-describedby={errors.email ? 'login-email-error' : undefined}
              placeholder="name@company.com"
            />
            {errors.email ? <small id="login-email-error">{errors.email}</small> : null}
          </label>

          <label className="field">
            <span>Password</span>
            <input
              type="password"
              name="password"
              value={form.password}
              onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
              autoComplete="current-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'login-password-error' : undefined}
              placeholder="Enter your password"
            />
            {errors.password ? <small id="login-password-error">{errors.password}</small> : null}
          </label>

          {errors.form ? <div className="form-error-banner">{errors.form}</div> : null}

          <button type="submit" className="primary-button" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in…' : 'Login'}
          </button>
        </form>

        <p className="auth-switch">
          Need an account? <a href="/register">Create one</a>
        </p>
      </div>
    </div>
  );
}

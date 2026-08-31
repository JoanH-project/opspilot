import { useState, type FormEvent, type ReactElement } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { useAuth } from '../features/auth/useAuth';
import type { ApiError } from '../types/auth';

type FormState = {
  name: string;
  email: string;
  password: string;
};

type FormErrors = Partial<Record<'name' | 'email' | 'password' | 'form', string>>;

const initialState: FormState = {
  name: '',
  email: '',
  password: '',
};

export function RegisterPage(): ReactElement {
  const navigate = useNavigate();
  const { register } = useAuth();
  const [form, setForm] = useState<FormState>(initialState);
  const [errors, setErrors] = useState<FormErrors>({});
  const [successMessage, setSuccessMessage] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validate = (): FormErrors => {
    const nextErrors: FormErrors = {};

    if (!form.name.trim()) {
      nextErrors.name = 'Name is required.';
    } else if (form.name.trim().length > 100) {
      nextErrors.name = 'Name must be 100 characters or fewer.';
    }

    if (!form.email.trim()) {
      nextErrors.email = 'Email is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      nextErrors.email = 'Enter a valid email address.';
    }

    if (!form.password) {
      nextErrors.password = 'Password is required.';
    } else if (form.password.length < 8 || form.password.length > 72) {
      nextErrors.password = 'Password must be between 8 and 72 characters.';
    }

    return nextErrors;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();

    const nextErrors = validate();
    setErrors(nextErrors);
    setSuccessMessage('');

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setIsSubmitting(true);

    try {
      await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
      });

      setSuccessMessage('Account created successfully. You can now sign in.');
      setForm(initialState);
      setErrors({});

      setTimeout(() => {
        navigate('/login', { replace: true });
      }, 200);
    } catch (error) {
      const apiError = error as ApiError;
      const fieldErrors = apiError.fieldErrors ?? {};
      const nextFormErrors: FormErrors = {
        form: fieldErrors.email ?? fieldErrors.password ?? apiError.message ?? 'Registration failed.',
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
          <h1>Create your account</h1>
          <p className="subtitle">Start managing your team operations in one place.</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <label className="field">
            <span>Name</span>
            <input
              type="text"
              name="name"
              value={form.name}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
              autoComplete="name"
              aria-invalid={Boolean(errors.name)}
              aria-describedby={errors.name ? 'register-name-error' : undefined}
              placeholder="Jane Doe"
            />
            {errors.name ? <small id="register-name-error">{errors.name}</small> : null}
          </label>

          <label className="field">
            <span>Email</span>
            <input
              type="email"
              name="email"
              value={form.email}
              onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
              autoComplete="email"
              aria-invalid={Boolean(errors.email)}
              aria-describedby={errors.email ? 'register-email-error' : undefined}
              placeholder="name@company.com"
            />
            {errors.email ? <small id="register-email-error">{errors.email}</small> : null}
          </label>

          <label className="field">
            <span>Password</span>
            <input
              type="password"
              name="password"
              value={form.password}
              onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
              autoComplete="new-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'register-password-error' : undefined}
              placeholder="Create a secure password"
            />
            {errors.password ? <small id="register-password-error">{errors.password}</small> : null}
          </label>

          {errors.form ? <div className="form-error-banner">{errors.form}</div> : null}
          {successMessage ? <div className="form-success-banner">{successMessage}</div> : null}

          <button type="submit" className="primary-button" disabled={isSubmitting}>
            {isSubmitting ? 'Creating account…' : 'Register'}
          </button>
        </form>

        <p className="auth-switch">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
}

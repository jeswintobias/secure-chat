import { Component, AfterViewInit, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { RegisterRequest } from '../../../shared/models';
import { environment } from '../../../../environments/environment';

declare const google: any;

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent implements AfterViewInit {
  email = '';
  username = '';
  useSameAsEmail = false;
  password = '';
  confirmPassword = '';
  isLoading = false;
  errorMessage = '';
  fieldErrors: Record<string, string> = {};
  googleLoading = false;

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly ngZone: NgZone,
  ) {}

  ngAfterViewInit(): void {
    this.initGoogleSignIn();
  }

  // ── "Same as email" toggle ──

  /** When toggled on, derive username from the email prefix. */
  onSameAsEmailToggle(): void {
    if (this.useSameAsEmail) {
      this.deriveUsernameFromEmail();
    }
  }

  /** Called on every email input keystroke to keep the username in sync. */
  onEmailInput(): void {
    if (this.useSameAsEmail) {
      this.deriveUsernameFromEmail();
    }
  }

  /** Extracts the part before '@' from the email and uses it as the username. */
  private deriveUsernameFromEmail(): void {
    const atIndex = this.email.indexOf('@');
    this.username = atIndex > 0 ? this.email.substring(0, atIndex) : this.email;
  }

  // ── Password strength validation (real-time) ──

  get hasMinLength(): boolean {
    return this.password.length >= 8;
  }

  get hasUppercase(): boolean {
    return /[A-Z]/.test(this.password);
  }

  get hasLowercase(): boolean {
    return /[a-z]/.test(this.password);
  }

  get hasNumber(): boolean {
    return /\d/.test(this.password);
  }

  get hasSpecial(): boolean {
    return /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?~`]/.test(this.password);
  }

  /** True when ALL password rules are satisfied. */
  get passwordValid(): boolean {
    return this.hasMinLength && this.hasUppercase && this.hasLowercase && this.hasNumber && this.hasSpecial;
  }

  get passwordMismatch(): boolean {
    return this.confirmPassword.length > 0 && this.password !== this.confirmPassword;
  }

  /** True when the password has been started (show rules). */
  get showPasswordRules(): boolean {
    return this.password.length > 0;
  }

  onSubmit(): void {
    if (this.passwordMismatch) return;
    if (!this.passwordValid) return;
    if (!this.username.trim() || !this.email.trim() || !this.password.trim()) return;

    this.isLoading = true;
    this.errorMessage = '';
    this.fieldErrors = {};

    const request: RegisterRequest = {
      username: this.username.trim(),
      email: this.email.trim(),
      password: this.password,
      confirmPassword: this.confirmPassword,
    };

    this.authService.register(request).subscribe({
      next: () => {
        this.router.navigate(['/chat']);
      },
      error: (err) => {
        this.isLoading = false;
        if (err?.error?.fieldErrors) {
          this.fieldErrors = err.error.fieldErrors;
        }
        this.errorMessage = err?.error?.message || 'Registration failed. Please try again.';
      },
    });
  }

  private initGoogleSignIn(): void {
    const checkGoogle = setInterval(() => {
      if (typeof google !== 'undefined' && google.accounts) {
        clearInterval(checkGoogle);
        google.accounts.id.initialize({
          client_id: environment.googleClientId,
          callback: (response: any) => this.handleGoogleResponse(response),
          auto_select: false,
        });
        google.accounts.id.renderButton(
          document.getElementById('google-signin-btn-register'),
          {
            theme: 'filled_black',
            size: 'large',
            width: 320,
            text: 'continue_with',
            shape: 'pill',
            logo_alignment: 'left',
          }
        );
      }
    }, 100);

    setTimeout(() => clearInterval(checkGoogle), 10000);
  }

  private handleGoogleResponse(response: any): void {
    this.ngZone.run(() => {
      this.googleLoading = true;
      this.errorMessage = '';

      this.authService.googleLogin(response.credential).subscribe({
        next: () => {
          this.router.navigate(['/chat']);
        },
        error: (err) => {
          this.googleLoading = false;
          this.errorMessage = err?.error?.message || 'Google sign-up failed. Please try again.';
        },
      });
    });
  }
}

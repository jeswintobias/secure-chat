import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { RegisterRequest } from '../../../shared/models';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent {
  username = '';
  email = '';
  password = '';
  confirmPassword = '';
  isLoading = false;
  errorMessage = '';
  fieldErrors: Record<string, string> = {};

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  get passwordMismatch(): boolean {
    return this.confirmPassword.length > 0 && this.password !== this.confirmPassword;
  }

  onSubmit(): void {
    if (this.passwordMismatch) return;
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
}

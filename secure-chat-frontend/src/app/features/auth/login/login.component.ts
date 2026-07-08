import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LoginRequest } from '../../../shared/models';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  username = '';
  password = '';
  isLoading = false;
  errorMessage = '';

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  onSubmit(): void {
    if (!this.username.trim() || !this.password.trim()) return;

    this.isLoading = true;
    this.errorMessage = '';

    const request: LoginRequest = {
      username: this.username.trim(),
      password: this.password,
    };

    this.authService.login(request).subscribe({
      next: () => {
        this.router.navigate(['/chat']);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Invalid credentials. Please try again.';
      },
    });
  }
}

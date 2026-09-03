import { Component, AfterViewInit, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LoginRequest } from '../../../shared/models';
import { environment } from '../../../../environments/environment';

declare const google: any;

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent implements AfterViewInit {
  username = '';
  password = '';
  isLoading = false;
  errorMessage = '';
  googleLoading = false;

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly ngZone: NgZone,
  ) {}

  ngAfterViewInit(): void {
    this.initGoogleSignIn();
  }

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

  private initGoogleSignIn(): void {
    // Wait for the GIS library to load (async script)
    const checkGoogle = setInterval(() => {
      if (typeof google !== 'undefined' && google.accounts) {
        clearInterval(checkGoogle);
        google.accounts.id.initialize({
          client_id: environment.googleClientId,
          callback: (response: any) => this.handleGoogleResponse(response),
          auto_select: false,
        });
        google.accounts.id.renderButton(
          document.getElementById('google-signin-btn-login'),
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

    // Stop checking after 10 seconds
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
          this.errorMessage = err?.error?.message || 'Google sign-in failed. Please try again.';
        },
      });
    });
  }
}

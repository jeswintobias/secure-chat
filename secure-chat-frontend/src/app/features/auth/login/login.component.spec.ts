import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/services/auth.service';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthResponse } from '../../../shared/models';

/**
 * Tests for the LoginComponent.
 *
 * Uses provideRouter([]) for a real Router instance (required by RouterLink),
 * then spies on Router.navigate for assertion.
 */
describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockAuthResponse: AuthResponse = {
    token: 'test-jwt-token',
    tokenType: 'Bearer',
    username: 'testuser',
    email: 'test@example.com',
    role: 'USER',
    expiresIn: 86400000,
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        provideRouter([]),
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should not submit when username and password are empty', () => {
    component.username = '';
    component.password = '';

    component.onSubmit();

    expect(authServiceSpy.login).not.toHaveBeenCalled();
  });

  it('should call authService.login and navigate to /chat on success', fakeAsync(() => {
    authServiceSpy.login.and.returnValue(of(mockAuthResponse));

    component.username = 'testuser';
    component.password = 'SecurePass1!';
    component.onSubmit();
    tick();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      username: 'testuser',
      password: 'SecurePass1!',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/chat']);
  }));
});

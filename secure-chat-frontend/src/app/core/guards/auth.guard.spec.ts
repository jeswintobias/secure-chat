import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

/**
 * Tests for the functional authGuard route guard.
 *
 * Verifies that:
 *  - Authenticated users can proceed to protected routes.
 *  - Unauthenticated users are redirected to /auth/login.
 */
describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated']);
    routerSpy = jasmine.createSpyObj('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('should allow navigation when user is authenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );

    expect(result).toBeTrue();
  });

  it('should redirect to /auth/login when user is not authenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);
    const fakeUrlTree = {} as UrlTree;
    routerSpy.createUrlTree.and.returnValue(fakeUrlTree);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );

    expect(result).toBe(fakeUrlTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/auth/login']);
  });

  it('should return a UrlTree (not false) when unauthenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);
    const fakeUrlTree = new (class extends UrlTree {})();
    routerSpy.createUrlTree.and.returnValue(fakeUrlTree);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );

    expect(result).toBeInstanceOf(UrlTree);
  });
});

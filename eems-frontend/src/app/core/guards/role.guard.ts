import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { UserRole } from '../models/auth.model';

/**
 * Route-level RBAC mirroring the backend's SecurityConfig rules.
 * This is a UX convenience only - the backend remains the source of
 * truth and re-checks every request; a hidden route is not a security
 * boundary on its own.
 */
export function roleGuard(...allowedRoles: UserRole[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (authService.hasAnyRole(...allowedRoles)) {
      return true;
    }
    router.navigate(['/dashboard']);
    return false;
  };
}

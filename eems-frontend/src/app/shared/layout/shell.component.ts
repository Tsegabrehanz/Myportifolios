import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../core/services/auth.service';
import { BrandingService } from '../../core/services/branding.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent {
  readonly currentYear = new Date().getFullYear();

  // Collapsed = icons-only sidebar, for smaller screens or a denser view -
  // a genuine interactive preference, not just decoration, and it
  // persists across sessions via localStorage so it doesn't reset every
  // time you reload.
  readonly sidebarCollapsed = signal(localStorage.getItem('eems_sidebar_collapsed') === 'true');

  constructor(public authService: AuthService, public brandingService: BrandingService, private router: Router) {
    this.brandingService.loadOnce();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  toggleSidebar(): void {
    const next = !this.sidebarCollapsed();
    this.sidebarCollapsed.set(next);
    localStorage.setItem('eems_sidebar_collapsed', String(next));
  }

  get isManagerOrAbove(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN', 'MANAGER');
  }

  get isHrOrAdmin(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN');
  }

  get canViewAnalytics(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN', 'AUDITOR');
  }

  get isSuperAdmin(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN');
  }

  get isAuditorOrAbove(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'AUDITOR');
  }
}

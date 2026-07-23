import { Injectable, signal } from '@angular/core';
import { AppSettingsApiService } from './app-settings-api.service';

/**
 * Fetches the custom company logo (if a SUPER_ADMIN has uploaded one)
 * once and caches it as a signal, so the login page and the shell don't
 * each independently fetch it. Falls back to the static logo.svg asset
 * (the default value here) if no custom logo has been set, or if the
 * fetch fails for any reason - a broken/missing logo should never be
 * the thing that makes the app unusable.
 */
@Injectable({ providedIn: 'root' })
export class BrandingService {
  readonly logoUrl = signal<string>('logo.svg');
  private loaded = false;
  private objectUrlToRevoke: string | null = null;

  constructor(private appSettingsApi: AppSettingsApiService) {}

  /** Safe to call multiple times - only actually fetches once per page load. */
  loadOnce(): void {
    if (this.loaded) {
      return;
    }
    this.loaded = true;

    this.appSettingsApi.status().subscribe({
      next: (status) => {
        if (!status.hasCustomLogo) {
          return; // stay on the default logo.svg
        }
        this.appSettingsApi.download().subscribe({
          next: (blob) => {
            const url = URL.createObjectURL(blob);
            this.objectUrlToRevoke = url;
            this.logoUrl.set(url);
          },
          error: () => {
            // Leave the default in place - a logo fetch failure is never worth surfacing as an error to the user.
          }
        });
      },
      error: () => {
        // Same reasoning - stay on the default silently.
      }
    });
  }

  /** Call after a successful upload/remove so the new (or reverted) logo shows immediately, without a full page reload. */
  refresh(): void {
    if (this.objectUrlToRevoke) {
      URL.revokeObjectURL(this.objectUrlToRevoke);
      this.objectUrlToRevoke = null;
    }
    this.loaded = false;
    this.logoUrl.set('logo.svg');
    this.loadOnce();
  }
}

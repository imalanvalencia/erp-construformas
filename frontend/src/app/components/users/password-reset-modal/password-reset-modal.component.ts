import { Component, input, output } from '@angular/core';
import { ButtonComponent } from '../../shared/button/button.component';

/**
 * S10: the admin password reset returns a plain-text password exactly once.
 * The modal exists so the value can be copied before it leaves the screen;
 * the backend only stores its hash and can never show it again.
 */
@Component({
  selector: 'app-password-reset-modal',
  standalone: true,
  imports: [ButtonComponent],
  template: `
    @if (show()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center">
        <div class="absolute inset-0 bg-black/50" (click)="onClose.emit()"></div>
        <div class="relative bg-white border border-steel w-full max-w-md mx-4 p-6 space-y-4">
          <h2 class="text-lg font-bold text-nero">Temporary password for {{ userName() }}</h2>
          <p class="font-mono text-xs text-steel">Copy it now — this password is shown only once.</p>
          <code class="block break-all bg-cement/50 border border-steel px-3 py-2 font-mono text-sm text-nero">{{ temporaryPassword() }}</code>
          <p class="font-mono text-xs text-steel">
            The user's sessions were revoked and a password change is forced on the next login.
          </p>
          <div class="flex justify-end pt-2">
            <app-button variant="filled" (click)="onClose.emit()">Done</app-button>
          </div>
        </div>
      </div>
    }
  `,
})
export class PasswordResetModalComponent {
  show = input(false);
  userName = input('');
  temporaryPassword = input('');

  onClose = output<void>();
}

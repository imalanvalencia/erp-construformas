import { Component, input, output } from '@angular/core';
import { User } from '../../../features/users/types/user.types';
import { ButtonComponent } from '../../shared/button/button.component';
import { CardComponent } from '../../shared/card/card.component';
import { BadgeComponent } from '../../shared/badge/badge.component';

@Component({
  selector: 'app-users-table',
  standalone: true,
  imports: [ButtonComponent, CardComponent, BadgeComponent],
  template: `
    <app-card>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-steel/30">
              <th class="text-left py-3 px-4 font-mono text-xs font-medium text-steel uppercase">Nombre</th>
              <th class="text-left py-3 px-4 font-mono text-xs font-medium text-steel uppercase">Email</th>
              <th class="text-left py-3 px-4 font-mono text-xs font-medium text-steel uppercase">Teléfono</th>
              <th class="text-left py-3 px-4 font-mono text-xs font-medium text-steel uppercase">NIE</th>
              <th class="text-left py-3 px-4 font-mono text-xs font-medium text-steel uppercase">Rol</th>
              <th class="text-left py-3 px-4 font-mono text-xs font-medium text-steel uppercase">Estado</th>
              <th class="text-right py-3 px-4 font-mono text-xs font-medium text-steel uppercase">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (user of users(); track user.id) {
              <tr class="border-b border-steel/10 hover:bg-cement/50">
                <td class="py-3 px-4 font-medium text-nero">{{ user.name }}</td>
                <td class="py-3 px-4 text-steel">{{ user.email }}</td>
                <td class="py-3 px-4 text-steel">{{ user.phone || '—' }}</td>
                <td class="py-3 px-4 text-steel font-mono">{{ user.nie || '—' }}</td>
                <td class="py-3 px-4">
                  @for (role of user.roles; track role.id) {
                    <app-badge [status]="role.name" />
                  } @empty {
                    <span class="text-steel">—</span>
                  }
                </td>
                <td class="py-3 px-4">
                  <app-badge [status]="user.active ? 'ACTIVE' : 'INACTIVE'" />
                </td>
                <td class="py-3 px-4 text-right space-x-2">
                  <app-button variant="text" size="sm" (click)="onEdit.emit(user)">Editar</app-button>
                  <app-button variant="text" size="sm" (click)="onResetPassword.emit(user)">Reset password</app-button>
                  <app-button variant="text" size="sm" (click)="onAssignProject.emit(user)">Project</app-button>
                  <app-button variant="text" size="sm" (click)="onToggleActive.emit(user)">
                    {{ user.active ? 'Deactivate' : 'Activate' }}
                  </app-button>
                  <app-button variant="text" size="sm" (click)="onDelete.emit(user.id!)">Eliminar</app-button>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="py-8 text-center text-steel">No hay usuarios registrados.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </app-card>
  `,
})
export class UsersTableComponent {
  users = input<User[]>([]);

  onEdit = output<User>();
  onDelete = output<number>();
  /** S9: `PATCH /api/users/{id}/active` with the inverted `active` flag. */
  onToggleActive = output<User>();
  /** S10: `POST /api/users/{id}/password-reset` — one-shot temporary password. */
  onResetPassword = output<User>();
  /** S11: `PUT /api/users/{id}/current-project`. */
  onAssignProject = output<User>();
}

import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Role, User, roleLabel } from '../../../features/users/types/user.types';
import { ButtonComponent } from '../../shared/button/button.component';
import { CheckboxComponent } from '../../shared/checkbox/checkbox.component';
import { InputComponent } from '../../shared/input/input.component';

@Component({
  selector: 'app-user-form-modal',
  standalone: true,
  imports: [FormsModule, ButtonComponent, CheckboxComponent, InputComponent],
  template: `
    @if (show()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center">
        <div class="absolute inset-0 bg-black/50" (click)="onClose.emit()"></div>
        <div class="relative bg-white border border-steel w-full max-w-lg mx-4 p-6 space-y-4 max-h-[90vh] overflow-y-auto">
          <h2 class="text-lg font-bold text-nero">{{ isEditing() ? 'Editar Usuario' : 'Nuevo Usuario' }}</h2>

          <app-input label="Nombre *" [value]="form().name ?? ''" (valueChange)="updateField('name', $event)" />
          <app-input label="Email *" type="email" [value]="form().email ?? ''" (valueChange)="updateField('email', $event)" />
          <app-input label="Teléfono" [value]="form().phone ?? ''" (valueChange)="updateField('phone', $event)" />
          <app-input label="NIE" [value]="form().nie ?? ''" (valueChange)="updateField('nie', $event)" />

          @if (!isEditing()) {
            <app-input label="Contraseña *" type="password" [value]="form().password ?? ''" (valueChange)="updateField('password', $event)" />
          }

          <fieldset class="pt-2">
            <legend class="font-mono text-xs font-medium text-steel">Roles *</legend>
            <div class="flex flex-col gap-2 pt-2">
              @for (role of roleCatalog(); track role.id) {
                <app-checkbox
                  [label]="roleLabel(role.name)"
                  [checked]="hasRole(role)"
                  (checkedChange)="toggleRole(role, $event)"
                />
              } @empty {
                <span class="font-mono text-xs text-steel">No hay roles disponibles.</span>
              }
            </div>
            @if (rolesError()) {
              <div class="font-mono text-xs text-construction-red mt-1">{{ rolesError() }}</div>
            }
          </fieldset>

          <div class="flex justify-end gap-3 pt-2">
            <app-button variant="text" (click)="onClose.emit()">Cancelar</app-button>
            <app-button variant="filled" (click)="onSave.emit()">
              {{ isEditing() ? 'Actualizar' : 'Crear' }}
            </app-button>
          </div>
        </div>
      </div>
    }
  `,
})
export class UserFormModalComponent {
  show = input(false);
  form = input<Partial<User>>({});
  isEditing = input(false);
  /** Role catalog with real ids, loaded from GET /api/roles. */
  roleCatalog = input<Role[]>([]);
  rolesError = input('');

  onClose = output<void>();
  onSave = output<void>();
  onFormChange = output<Partial<User>>();

  readonly roleLabel = roleLabel;

  hasRole(role: Role): boolean {
    return (this.form().roles ?? []).some((r) => r.id === role.id);
  }

  toggleRole(role: Role, checked: boolean): void {
    const current = this.form().roles ?? [];
    const next = checked
      ? [...current.filter((r) => r.id !== role.id), role]
      : current.filter((r) => r.id !== role.id);
    this.onFormChange.emit({ roles: next });
  }

  updateField(field: keyof User, value: string): void {
    this.onFormChange.emit({ [field]: value });
  }
}

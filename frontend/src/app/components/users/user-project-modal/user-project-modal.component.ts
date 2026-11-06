import { Component, effect, input, output } from '@angular/core';
import { Project } from '../../../features/projects/types/project.types';
import { ButtonComponent } from '../../shared/button/button.component';
import { SelectComponent } from '../../shared/select/select.component';

/**
 * S11: an ADMIN points any user at any project through
 * `PUT /api/users/{id}/current-project`. Selection is confirmed explicitly so a
 * stray click never reassigns the current project.
 */
@Component({
  selector: 'app-user-project-modal',
  standalone: true,
  imports: [ButtonComponent, SelectComponent],
  template: `
    @if (show()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center">
        <div class="absolute inset-0 bg-black/50" (click)="onClose.emit()"></div>
        <div class="relative bg-white border border-steel w-full max-w-md mx-4 p-6 space-y-4">
          <h2 class="text-lg font-bold text-nero">Assign project — {{ userName() }}</h2>

          <app-select
            label="Current project"
            placeholder="Select a project"
            [options]="projectOptions"
            [value]="selected"
            (valueChange)="onSelectionChange($event)"
          />

          <div class="flex justify-end gap-3 pt-2">
            <app-button variant="text" (click)="onClose.emit()">Cancel</app-button>
            <app-button variant="filled" [disabled]="!canAssign" (click)="assign()">Assign</app-button>
          </div>
        </div>
      </div>
    }
  `,
})
export class UserProjectModalComponent {
  show = input(false);
  userName = input('');
  projects = input<Project[]>([]);
  currentProjectId = input<number | undefined>(undefined);

  onClose = output<void>();
  onAssign = output<number>();

  selected: number | undefined;

  constructor() {
    // Reset the pick every time the modal opens so a previous selection never leaks in.
    effect(() => {
      if (this.show()) this.selected = this.currentProjectId();
    });
  }

  get projectOptions(): { value: number; label: string }[] {
    return this.projects()
      .filter((project): project is Project & { id: number } => project.id != null)
      .map((project) => ({ value: project.id, label: project.name }));
  }

  get canAssign(): boolean {
    return this.selected !== undefined && this.selected !== this.currentProjectId();
  }

  onSelectionChange(value: unknown): void {
    const parsed = Number(value);
    this.selected = Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
  }

  assign(): void {
    if (!this.canAssign || this.selected === undefined) return;
    this.onAssign.emit(this.selected);
  }
}

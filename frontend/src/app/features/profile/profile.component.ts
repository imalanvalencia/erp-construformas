import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ProfileService } from './services/profile.service';
import { ProfileContactUpdate, ProfileSession, UserProfile } from './types/profile.types';
import { ClockEntry } from '../clock/types/clock.types';
import { Project } from '../projects/types/project.types';
import { ProjectService } from '../projects/services/project.service';
import { AVAILABILITY_LABELS } from '../users/types/user.types';
import { NotificationService } from '@app/services/notification.service';
import { ButtonComponent } from '@shared-components/button/button.component';
import { CardComponent } from '@shared-components/card/card.component';
import { InputComponent } from '@shared-components/input/input.component';
import { SelectComponent, SelectOption } from '@shared-components/select/select.component';
import { BadgeComponent } from '@shared-components/badge/badge.component';
import { TableComponent, TableColumn } from '@shared-components/table/table.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    ButtonComponent,
    CardComponent,
    InputComponent,
    SelectComponent,
    BadgeComponent,
    TableComponent,
  ],
  template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-bold text-nero">Mi perfil</h1>

      <!-- S2: read-only identity, sourced from GET /api/users/me. -->
      <app-card category="Cuenta" title="Identidad">
        @if (profile(); as p) {
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <div class="font-mono text-xs text-steel">Nombre</div>
              <div class="text-sm text-nero">{{ p.name }}</div>
            </div>
            <div>
              <div class="font-mono text-xs text-steel">Email</div>
              <div class="text-sm text-nero">{{ p.email }}</div>
            </div>
            <div>
              <div class="font-mono text-xs text-steel">NIE</div>
              <div class="text-sm text-nero">{{ p.nie || '—' }}</div>
            </div>
            <div>
              <div class="font-mono text-xs text-steel">Teléfono</div>
              <div class="text-sm text-nero">{{ p.phone || '—' }}</div>
            </div>
            <div>
              <div class="font-mono text-xs text-steel">Roles</div>
              <div class="flex gap-2 mt-1">
                @for (role of p.roles; track role.id) {
                  <app-badge [status]="role.name" />
                } @empty {
                  <span class="text-sm text-steel">—</span>
                }
              </div>
            </div>
            <div>
              <div class="font-mono text-xs text-steel">Disponibilidad</div>
              <div class="mt-1">
                <app-badge [status]="availabilityLabel(p)" />
              </div>
            </div>
            <div>
              <div class="font-mono text-xs text-steel">Proyecto actual</div>
              <div class="text-sm text-nero">{{ p.currentProjectName || 'Sin proyecto asignado' }}</div>
            </div>
            <div>
              <div class="font-mono text-xs text-steel">Alta</div>
              <div class="text-sm text-nero">{{ formatDate(p.createdAt) }}</div>
            </div>
          </div>
        }
      </app-card>

      <!-- S3: the owner edits only phone and nie; nothing else is writable here. -->
      <app-card category="Datos de contacto" title="Contacto">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <app-input
            label="Teléfono"
            [value]="contactPhone"
            (valueChange)="contactPhone = $event"
          />
          <app-input label="NIE" [value]="contactNie" (valueChange)="contactNie = $event" />
        </div>
        <div class="mt-4">
          <app-button variant="filled" (click)="saveContact()">Guardar cambios</app-button>
        </div>
      </app-card>

      <!-- S4: current + new + confirmation, posted to /api/users/me/password. -->
      <app-card category="Seguridad" title="Cambiar contraseña">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <app-input
            type="password"
            label="Contraseña actual"
            [value]="currentPassword"
            (valueChange)="currentPassword = $event"
          />
          <app-input
            type="password"
            label="Nueva contraseña"
            [value]="newPassword"
            (valueChange)="newPassword = $event"
          />
          <app-input
            type="password"
            label="Confirmar contraseña"
            [value]="confirmPassword"
            (valueChange)="confirmPassword = $event"
          />
        </div>
        <div class="mt-4">
          <app-button variant="filled" (click)="savePassword()">Actualizar contraseña</app-button>
        </div>
      </app-card>

      <!-- S6: current project plus the switcher over the caller's assigned projects. -->
      <app-card category="Trabajo" title="Proyecto actual">
        @if (profile(); as p) {
          <div class="mb-4">
            <div class="font-mono text-xs text-steel">Proyecto actual</div>
            <div class="text-sm text-nero">
              {{ p.currentProjectName || 'Sin proyecto asignado' }}
            </div>
          </div>
        }
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 items-end">
          <app-select
            label="Cambiar de proyecto"
            [options]="projectOptions()"
            [value]="selectedProjectId"
            (valueChange)="selectedProjectId = $event"
            placeholder="Seleccioná una obra"
          />
          <div>
            <app-button variant="filled" (click)="saveProject()">Cambiar proyecto</app-button>
          </div>
        </div>
      </app-card>

      <!-- S7: the caller's own entries plus week/month hours. -->
      <app-card category="Fichajes" title="Mis fichajes">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
          <div>
            <div class="font-mono text-xs uppercase text-steel">Horas esta semana</div>
            <div class="text-xl font-bold text-nero">{{ weekHours() }}</div>
          </div>
          <div>
            <div class="font-mono text-xs uppercase text-steel">Horas este mes</div>
            <div class="text-xl font-bold text-nero">{{ monthHours() }}</div>
          </div>
        </div>
        <app-table [columns]="entryColumns" [data]="entryRows()" />
      </app-card>

      <!-- S8: active refresh tokens and closing the other sessions. -->
      <app-card category="Seguridad" title="Mis sesiones">
        <app-table [columns]="sessionColumns" [data]="sessionRows()" />
        <div class="mt-4">
          <app-button variant="text" (click)="closeOtherSessions()">
            Cerrar las demás sesiones
          </app-button>
        </div>
      </app-card>

      <!--
        Reserved section (S13): future self-service modules (notifications,
        linked devices, preferences, …) land here as new cards without
        reworking the sections above.
      -->
      <app-card category="Próximamente" title="Reservado para futuros módulos">
        <p class="font-mono text-xs text-steel">
          Espacio reservado para futuros módulos del perfil.
        </p>
      </app-card>
    </div>
  `,
})
export class ProfileComponent {
  profile = signal<UserProfile | null>(null);
  projects = signal<Project[]>([]);
  sessions = signal<ProfileSession[]>([]);
  weekEntries = signal<ClockEntry[]>([]);
  monthEntries = signal<ClockEntry[]>([]);

  contactPhone = '';
  contactNie = '';
  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  selectedProjectId = '';

  readonly entryColumns: TableColumn[] = [
    { key: 'timestamp', label: 'Fecha/Hora' },
    { key: 'project', label: 'Proyecto' },
    { key: 'clockType', label: 'Tipo' },
  ];

  readonly sessionColumns: TableColumn[] = [
    { key: 'id', label: 'Sesión' },
    { key: 'createdAt', label: 'Creada' },
    { key: 'expiry', label: 'Expira' },
  ];

  readonly projectOptions = computed<SelectOption<string>[]>(() =>
    this.projects().map((project) => ({ value: String(project.id ?? ''), label: project.name })),
  );

  readonly weekHours = computed(() =>
    this.formatHours(this.profileService.calculateHours(this.weekEntries())),
  );

  readonly monthHours = computed(() =>
    this.formatHours(this.profileService.calculateHours(this.monthEntries())),
  );

  readonly entryRows = computed<Record<string, string>[]>(() =>
    [...this.monthEntries()]
      .sort((a, b) => Date.parse(b.timestamp ?? '') - Date.parse(a.timestamp ?? ''))
      .map((entry) => ({
        timestamp: this.formatDateTime(entry.timestamp),
        project: entry.projectName ?? '—',
        clockType: entry.clockType === 'ENTRY' ? 'Entrada' : 'Salida',
      })),
  );

  readonly sessionRows = computed<Record<string, string>[]>(() =>
    this.sessions().map((session) => ({
      id: session.id,
      createdAt: this.formatDateTime(session.createdAt),
      expiry: this.formatDateTime(session.expiry),
    })),
  );

  private destroyRef = inject(DestroyRef);
  private profileService = inject(ProfileService);
  private projectService = inject(ProjectService);
  private notifications = inject(NotificationService);

  constructor() {
    this.loadProfile();
    this.loadProjects();
    this.loadSessions();
    this.loadEntries();
  }

  saveContact(): void {
    const update: ProfileContactUpdate = { phone: this.contactPhone, nie: this.contactNie };
    this.profileService
      .updateContact(update)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this.applyProfile(profile);
          this.notifications.success('Contacto actualizado correctamente.');
        },
        error: (err) =>
          this.notifyError(err, 'No se pudo actualizar el contacto. Inténtalo de nuevo.'),
      });
  }

  savePassword(): void {
    if (this.newPassword !== this.confirmPassword) {
      this.notifications.error('Las contraseñas no coinciden.');
      return;
    }
    if (!this.currentPassword || !this.newPassword) {
      this.notifications.error('Completá la contraseña actual y la nueva.');
      return;
    }

    this.profileService
      .changePassword({ currentPassword: this.currentPassword, newPassword: this.newPassword })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.currentPassword = '';
          this.newPassword = '';
          this.confirmPassword = '';
          this.notifications.success('Contraseña actualizada correctamente.');
        },
        error: (err) =>
          this.notifyError(err, 'No se pudo actualizar la contraseña. Inténtalo de nuevo.'),
      });
  }

  saveProject(): void {
    const projectId = Number(this.selectedProjectId);
    if (!projectId) {
      this.notifications.error('Seleccioná un proyecto.');
      return;
    }

    this.profileService
      .setCurrentProject(projectId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this.applyProfile(profile);
          this.notifications.success('Proyecto actual actualizado.');
        },
        error: (err) =>
          this.notifyError(err, 'No se pudo cambiar el proyecto actual. Inténtalo de nuevo.'),
      });
  }

  closeOtherSessions(): void {
    this.profileService
      .closeOtherSessions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.notifications.success('Se cerraron las demás sesiones.');
          this.loadSessions();
        },
        error: (err) =>
          this.notifyError(err, 'No se pudieron cerrar las sesiones. Inténtalo de nuevo.'),
      });
  }

  availabilityLabel(profile: UserProfile): string {
    return AVAILABILITY_LABELS[profile.availability] ?? profile.availability;
  }

  formatDate(iso?: string | null): string {
    const date = this.parse(iso);
    if (!date) return '—';
    return `${this.pad(date.getDate())}/${this.pad(date.getMonth() + 1)}/${date.getFullYear()}`;
  }

  formatDateTime(iso?: string | null): string {
    const date = this.parse(iso);
    if (!date) return '—';
    return `${this.formatDate(iso)} ${this.pad(date.getHours())}:${this.pad(date.getMinutes())}`;
  }

  private parse(iso?: string | null): Date | null {
    if (!iso) return null;
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private pad(value: number): string {
    return String(value).padStart(2, '0');
  }

  private formatHours(hours: number): string {
    return `${hours.toFixed(1).replace('.', ',')} h`;
  }

  private applyProfile(profile: UserProfile): void {
    this.profile.set(profile);
    this.contactPhone = profile.phone ?? '';
    this.contactNie = profile.nie ?? '';
    this.selectedProjectId = String(profile.currentProjectId ?? '');
  }

  private notifyError(err: unknown, fallback: string): void {
    const body = (err as HttpErrorResponse | undefined)?.error;
    const serverMessage =
      body !== null && typeof body === 'object'
        ? (body as { error?: unknown }).error
        : undefined;
    this.notifications.error(
      typeof serverMessage === 'string' && serverMessage ? serverMessage : fallback,
    );
  }

  private loadProfile(): void {
    this.profileService
      .getMe()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (profile) => this.applyProfile(profile) });
  }

  private loadProjects(): void {
    this.projectService
      .getAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (projects) => this.projects.set(projects) });
  }

  private loadSessions(): void {
    this.profileService
      .getSessions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (sessions) => this.sessions.set(sessions) });
  }

  private loadEntries(): void {
    const week = this.profileService.currentWeekRange();
    const month = this.profileService.currentMonthRange();

    this.profileService
      .getMyClockEntries(week.start, week.end)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (entries) => this.weekEntries.set(entries) });

    this.profileService
      .getMyClockEntries(month.start, month.end)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (entries) => this.monthEntries.set(entries) });
  }
}

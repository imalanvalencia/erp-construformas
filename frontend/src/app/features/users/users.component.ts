import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserService } from './services/user.service';
import { Role, User } from './types/user.types';
import { ProjectService } from '@features/projects/services/project.service';
import { Project } from '@features/projects/types/project.types';
import { ButtonComponent } from '@shared-components/button/button.component';
import { UsersTableComponent } from '@components/users/users-table/users-table.component';
import { UserFormModalComponent } from '@components/users/user-form-modal/user-form-modal.component';
import { UserProjectModalComponent } from '@components/users/user-project-modal/user-project-modal.component';
import { PasswordResetModalComponent } from '@components/users/password-reset-modal/password-reset-modal.component';
import { UsersFiltersComponent } from '@components/users/users-filters/users-filters.component';
import { NotificationService } from '@app/services/notification.service';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    ButtonComponent,
    UsersTableComponent,
    UserFormModalComponent,
    UserProjectModalComponent,
    PasswordResetModalComponent,
    UsersFiltersComponent,
  ],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-nero">Usuarios</h1>
        <app-button variant="filled" (click)="openCreateModal()">+ Nuevo Usuario</app-button>
      </div>

      <app-users-filters [filterRole]="filterRole" (onFilterChange)="onFilterChange($event)" />

      <app-users-table
        [users]="filteredUsers()"
        (onEdit)="openEditModal($event)"
        (onDelete)="deleteUser($event)"
        (onToggleActive)="toggleActive($event)"
        (onResetPassword)="resetPassword($event)"
        (onAssignProject)="openProjectModal($event)"
      />

      <app-user-form-modal
        [show]="showModal()"
        [form]="formData"
        [isEditing]="!!editingUser"
        [roleCatalog]="roleCatalog()"
        [rolesError]="rolesError()"
        (onClose)="closeModal()"
        (onSave)="saveUser()"
        (onFormChange)="onFormChange($event)"
      />

      <app-user-project-modal
        [show]="showProjectModal()"
        [userName]="projectModalUser?.name ?? ''"
        [projects]="projects()"
        [currentProjectId]="projectModalUser?.currentProjectId"
        (onClose)="closeProjectModal()"
        (onAssign)="assignProject($event)"
      />

      <app-password-reset-modal
        [show]="!!resetResult()"
        [userName]="resetResult()?.userName ?? ''"
        [temporaryPassword]="resetResult()?.temporaryPassword ?? ''"
        (onClose)="resetResult.set(null)"
      />
    </div>
  `,
})
export class UsersComponent {
  users = signal<User[]>([]);
  filteredUsers = signal<User[]>([]);
  roleCatalog = signal<Role[]>([]);
  filterRole = '';
  showModal = signal(false);
  editingUser: User | null = null;
  formData: Partial<User> = this.emptyForm();
  rolesError = signal('');

  /** S10: the one-shot reset result, rendered by the confirmation modal. */
  resetResult = signal<{ userName: string; temporaryPassword: string } | null>(null);

  /** S11: the user whose current project is being assigned. */
  projectModalUser: User | null = null;
  showProjectModal = signal(false);
  projects = signal<Project[]>([]);
  private projectsLoaded = false;

  private loadVersion = 0;

  private destroyRef = inject(DestroyRef);
  private userService = inject(UserService);
  private projectService = inject(ProjectService);
  private notifications = inject(NotificationService);

  constructor() {
    this.loadUsers();
    this.loadRoles();
  }

  loadRoles(): void {
    this.userService
      .getRoles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (roles) => this.roleCatalog.set(roles) });
  }

  loadUsers(): void {
    const requestVersion = ++this.loadVersion;
    this.userService.getAll().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        if (requestVersion !== this.loadVersion) return;
        this.users.set(data);
        this.filteredUsers.set(this.matchesActiveFilter(data));
      },
    });
  }

  /** Client-side: `roles` is a set, so a server-side filter no longer applies. */
  onFilterChange(role: string): void {
    this.filterRole = role;
    this.filteredUsers.set(this.matchesActiveFilter(this.users()));
  }

  openCreateModal(): void {
    this.editingUser = null;
    this.formData = this.emptyForm();
    this.rolesError.set('');
    this.showModal.set(true);
  }

  openEditModal(user: User): void {
    this.editingUser = user;
    this.formData = { ...user, password: undefined };
    this.rolesError.set('');
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
    this.editingUser = null;
    this.formData = this.emptyForm();
    this.rolesError.set('');
  }

  onFormChange(change: Partial<User>): void {
    this.formData = { ...this.formData, ...change };
    if (change.roles?.length) this.rolesError.set('');
  }

  saveUser(): void {
    if (!this.formData.name?.trim() || !this.formData.email?.trim()) return;
    if (!this.editingUser && !this.formData.password?.trim()) return;

    const roles = this.formData.roles ?? [];
    if (roles.length === 0) {
      this.rolesError.set('Seleccioná al menos un rol.');
      return;
    }

    if (this.editingUser?.id) {
      this.updateExisting(this.editingUser.id, roles);
    } else {
      this.createOne(roles);
    }
  }

  private updateExisting(id: number, roles: Role[]): void {
    // Availability is automatic-only: it never travels in the PUT body anymore (S5).
    const payload = { ...this.formData, roles } as User;

    this.userService.update(id, payload).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.loadUsers();
        this.closeModal();
      },
      error: () => {
        this.notifications.error('No se pudo actualizar el usuario. Inténtalo de nuevo.');
      },
    });
  }

  private createOne(roles: Role[]): void {
    this.userService.create({ ...this.formData, roles, active: true } as User).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (user) => {
        this.loadVersion++;
        this.users.update((users) => [...users, user]);
        if (this.matchesActiveFilter([user]).length > 0) {
          this.filteredUsers.update((users) => [...users, user]);
        }
        this.closeModal();
        this.notifications.success('Usuario creado correctamente.');
      },
      error: () => {
        this.notifications.error('No se pudo crear el usuario. Inténtalo de nuevo.');
      },
    });
  }

  /** S9: the row toggle — activates or deactivates through `PATCH /{id}/active`. */
  toggleActive(user: User): void {
    if (!user.id) return;
    this.applyActive(user.id, !user.active);
  }

  private applyActive(id: number, active: boolean): void {
    this.userService.setActive(id, active).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.loadUsers(),
      error: (error) =>
        this.notifications.error(
          this.serverMessage(error, 'Could not update the account status.'),
        ),
    });
  }

  /** S10: shows the one-shot temporary password in the confirmation modal. */
  resetPassword(user: User): void {
    if (!user.id) return;
    this.userService.resetPassword(user.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) =>
        this.resetResult.set({
          userName: user.name,
          temporaryPassword: response.temporaryPassword,
        }),
      error: (error) =>
        this.notifications.error(
          this.serverMessage(error, 'Could not generate the temporary password.'),
        ),
    });
  }

  openProjectModal(user: User): void {
    this.projectModalUser = user;
    this.showProjectModal.set(true);
    if (this.projectsLoaded) return;
    this.projectService.getAll().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (projects) => {
        this.projectsLoaded = true;
        this.projects.set(projects);
      },
      error: (error) =>
        this.notifications.error(this.serverMessage(error, 'Could not load the projects.')),
    });
  }

  closeProjectModal(): void {
    this.showProjectModal.set(false);
    this.projectModalUser = null;
  }

  /** S11: `PUT /api/users/{id}/current-project`; 400/403 refusals surface verbatim. */
  assignProject(projectId: number): void {
    const id = this.projectModalUser?.id;
    if (!id) return;
    this.userService.setCurrentProject(id, projectId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.loadUsers();
        this.closeProjectModal();
        this.notifications.success('Current project updated.');
      },
      error: (error) =>
        this.notifications.error(
          this.serverMessage(error, 'Could not assign the current project.'),
        ),
    });
  }

  /**
   * S12: delete stays available, but the backend now refuses users with history.
   * The server message is shown verbatim and, when it points at deactivation,
   * the user is offered the supported path instead.
   */
  deleteUser(id: number): void {
    if (!confirm('¿Estás seguro de eliminar este usuario?')) return;
    this.userService.delete(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.loadUsers(),
      error: (error) => {
        const message = this.serverMessage(error, 'Could not delete the user.');
        this.notifications.error(message);
        const offersDeactivation = message.includes('deactivate the account instead');
        if (offersDeactivation && confirm('The server refused the delete. Deactivate the account instead?')) {
          this.applyActive(id, false);
        }
      },
    });
  }

  /** The backend's error envelope everywhere is `{ "error": "<message>" }`. */
  private serverMessage(error: unknown, fallback: string): string {
    const envelope = (error as { error?: { error?: unknown } } | null | undefined)?.error?.error;
    return typeof envelope === 'string' && envelope.length > 0 ? envelope : fallback;
  }

  private matchesActiveFilter(users: User[]): User[] {
    if (!this.filterRole) return users;
    return users.filter((user) => (user.roles ?? []).some((role) => role.name === this.filterRole));
  }

  private emptyForm(): Partial<User> {
    return {
      name: '',
      email: '',
      phone: '',
      nie: '',
      password: '',
      roles: [],
      active: true,
    };
  }
}

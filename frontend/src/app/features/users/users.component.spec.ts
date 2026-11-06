import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, Subject, throwError } from 'rxjs';
import { UsersComponent } from './users.component';
import { UserService } from './services/user.service';
import { Role, User } from './types/user.types';
import { ProjectService } from '@features/projects/services/project.service';
import { Project } from '@features/projects/types/project.types';
import { NotificationService } from '@app/services/notification.service';

const OPERATOR_ROLE: Role = { id: 2, name: 'OPERATOR', description: 'Field operator' };
const ADMIN_ROLE: Role = { id: 1, name: 'ADMIN', description: 'Administrator' };

const PROJECT: Project = {
  id: 7,
  clientId: 1,
  name: 'Calle 12',
  address: 'Calle 12',
  latitude: 40.4,
  longitude: -3.7,
  status: 'PLANNED',
  active: true,
};

describe('UsersComponent', () => {
  let component: UsersComponent;
  let fixture: ComponentFixture<UsersComponent>;
  let userService: {
    getAll: ReturnType<typeof vi.fn>;
    getRoles: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    setActive: ReturnType<typeof vi.fn>;
    resetPassword: ReturnType<typeof vi.fn>;
    setCurrentProject: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let projectService: { getAll: ReturnType<typeof vi.fn> };
  let notifications: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  const existingUser: User = {
    id: 1,
    name: 'Ana Uno',
    email: 'ana@example.com',
    roles: [OPERATOR_ROLE],
    active: true,
  };

  beforeEach(async () => {
    userService = {
      getAll: vi.fn().mockReturnValue(of([existingUser])),
      getRoles: vi.fn().mockReturnValue(of([ADMIN_ROLE, OPERATOR_ROLE])),
      create: vi.fn(),
      update: vi.fn(),
      setActive: vi.fn(),
      resetPassword: vi.fn(),
      setCurrentProject: vi.fn(),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    projectService = { getAll: vi.fn().mockReturnValue(of([PROJECT])) };
    notifications = { success: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [UsersComponent],
      providers: [
        { provide: UserService, useValue: userService },
        { provide: ProjectService, useValue: projectService },
        { provide: NotificationService, useValue: notifications },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UsersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders one badge per role taken from the roles set, not the removed single role', () => {
    component.filteredUsers.set([{ ...existingUser, roles: [ADMIN_ROLE, OPERATOR_ROLE] }]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('ADMIN');
    expect(text).toContain('OPERATOR');
  });

  it('sends the many-to-many roles payload on create (S6)', () => {
    userService.create.mockReturnValue(of({ ...existingUser, id: 2 }));
    component.openCreateModal();
    component.onFormChange({
      name: 'Ana Dos',
      email: 'ana2@example.com',
      password: 'secreta',
      roles: [OPERATOR_ROLE],
    });

    component.saveUser();

    expect(userService.create).toHaveBeenCalledWith(
      expect.objectContaining({ roles: [OPERATOR_ROLE] }),
    );
  });

  it('filters by role client-side over the roles set (S4)', () => {
    const admin = { ...existingUser, id: 3, name: 'Beto', roles: [ADMIN_ROLE] };
    userService.getAll.mockReturnValue(of([existingUser, admin]));
    component.loadUsers();

    component.onFilterChange('ADMIN');

    expect(component.filteredUsers()).toEqual([admin]);
  });

  it('restores the full list when the role filter is cleared (S4)', () => {
    const admin = { ...existingUser, id: 3, name: 'Beto', roles: [ADMIN_ROLE] };
    userService.getAll.mockReturnValue(of([existingUser, admin]));
    component.loadUsers();

    component.onFilterChange('ADMIN');
    component.onFilterChange('');

    expect(component.filteredUsers()).toEqual([existingUser, admin]);
  });

  it('appends the confirmed user once to canonical and unfiltered visible state', () => {
    const savedUser = {
      ...existingUser,
      id: 2,
      roles: [ADMIN_ROLE],
    };
    userService.create.mockReturnValue(of(savedUser));
    component.openCreateModal();
    component.onFormChange({ ...savedUser, password: 'secreta' });

    component.saveUser();

    expect(component.users()).toEqual([existingUser, savedUser]);
    expect(component.filteredUsers()).toEqual([existingUser, savedUser]);
    expect(component.showModal()).toBe(false);
    expect(notifications.success).toHaveBeenCalledWith('Usuario creado correctamente.');
    expect(userService.getAll).toHaveBeenCalledTimes(1);
  });

  it('respects the active role filter when appending a confirmed user', () => {
    const admin = { ...existingUser, id: 2, roles: [ADMIN_ROLE] };
    userService.create.mockReturnValue(of(admin));
    component.onFilterChange('OPERATOR');
    component.openCreateModal();
    component.onFormChange({ ...admin, password: 'secreta' });

    component.saveUser();

    expect(component.users()).toEqual([existingUser, admin]);
    expect(component.filteredUsers()).toEqual([existingUser]);
  });

  it('preserves the list and form on create error and notifies the user', () => {
    const form = { ...existingUser, name: 'Ana Dos', password: 'secreta' };
    userService.create.mockReturnValue(throwError(() => new Error('Network failure')));
    component.openCreateModal();
    component.onFormChange(form);

    component.saveUser();

    expect(component.users()).toEqual([existingUser]);
    expect(component.showModal()).toBe(true);
    expect(component.formData).toMatchObject(form);
    expect(notifications.error).toHaveBeenCalledWith(
      'No se pudo crear el usuario. Inténtalo de nuevo.',
    );
  });

  it('does not let a stale list response overwrite a successful create', () => {
    const pendingLoad = new Subject<User[]>();
    const savedUser = { ...existingUser, id: 2, password: 'secreta' };
    userService.getAll.mockReturnValue(pendingLoad);
    userService.create.mockReturnValue(of(savedUser));
    fixture.destroy();
    fixture = TestBed.createComponent(UsersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.formData = savedUser;

    component.saveUser();
    pendingLoad.next([existingUser]);

    expect(component.users()).toEqual([savedUser]);
    expect(component.filteredUsers()).toEqual([savedUser]);
  });

  it('still saves the edit form through PUT /api/users/{id} without an availability side channel (S5)', () => {
    userService.update.mockReturnValue(of({ ...existingUser, name: 'Ana Editada' }));
    component.openEditModal(existingUser);
    component.onFormChange({ name: 'Ana Editada' });

    component.saveUser();

    expect(userService.update).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ name: 'Ana Editada', roles: [OPERATOR_ROLE] }),
    );
    expect(userService.update).toHaveBeenCalledTimes(1);
  });

  it('removes every manual availability control from the form and the table (S5)', () => {
    component.openEditModal(existingUser);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Disponibilidad');
    expect(component['emptyForm']()).not.toHaveProperty('availability');
  });

  it('asks for confirmation before deleting a user', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.deleteUser(1);

    expect(userService.delete).toHaveBeenCalledWith(1);
    confirmSpy.mockRestore();
  });

  it('toggles the row user between active and inactive through PATCH /{id}/active (S9)', () => {
    userService.setActive.mockReturnValue(of({ ...existingUser, active: false }));

    component.toggleActive(existingUser);

    expect(userService.setActive).toHaveBeenCalledWith(1, false);
    expect(userService.getAll).toHaveBeenCalledTimes(2);

    component.toggleActive({ ...existingUser, active: false });

    expect(userService.setActive).toHaveBeenCalledWith(1, true);
  });

  it('surfaces the server refusal when deactivation is blocked (S9)', () => {
    for (const message of [
      'You cannot deactivate your own account',
      'Cannot remove the last active administrator',
    ]) {
      userService.setActive.mockReturnValue(throwError(() => ({ error: { error: message } })));

      component.toggleActive(existingUser);

      expect(notifications.error).toHaveBeenCalledWith(message);
    }
  });

  it('shows the temporary password once after an admin reset (S10)', () => {
    userService.resetPassword.mockReturnValue(of({ temporaryPassword: 'Tmp-4x9K!' }));

    component.resetPassword(existingUser);
    fixture.detectChanges();

    expect(userService.resetPassword).toHaveBeenCalledWith(1);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Tmp-4x9K!');
    expect(text).toContain('sessions were revoked');
    expect(text).toContain('next login');
  });

  it('surfaces a failed reset from the error envelope (S10)', () => {
    userService.resetPassword.mockReturnValue(
      throwError(() => ({ error: { error: 'User not found' } })),
    );

    component.resetPassword(existingUser);
    fixture.detectChanges();

    expect(notifications.error).toHaveBeenCalledWith('User not found');
    expect(component.resetResult()).toBeNull();
  });

  it('assigns the current project through PUT /{id}/current-project (S11)', () => {
    userService.setCurrentProject.mockReturnValue(
      of({ ...existingUser, currentProjectId: 7, currentProjectName: 'Calle 12' }),
    );

    component.openProjectModal(existingUser);
    expect(projectService.getAll).toHaveBeenCalledTimes(1);

    component.assignProject(7);

    expect(userService.setCurrentProject).toHaveBeenCalledWith(1, 7);
    expect(component.showProjectModal()).toBe(false);
    expect(notifications.success).toHaveBeenCalledWith('Current project updated.');
    expect(userService.getAll).toHaveBeenCalledTimes(2);
  });

  it('lists the available projects in the assignment modal (S11)', () => {
    component.openProjectModal(existingUser);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Calle 12');
  });

  it('surfaces every assignment refusal from the error envelope (S11)', () => {
    component.openProjectModal(existingUser);

    for (const message of [
      'Project not found: 99',
      'projectId is required',
      'You are not assigned to this project',
    ]) {
      userService.setCurrentProject.mockReturnValue(throwError(() => ({ error: { error: message } })));

      component.assignProject(99);

      expect(notifications.error).toHaveBeenCalledWith(message);
    }
  });

  it('shows the history refusal on delete and deactivates instead (S12)', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    userService.delete.mockReturnValue(
      throwError(() => ({ error: { error: 'User has activity history; deactivate the account instead' } })),
    );
    userService.setActive.mockReturnValue(of({ ...existingUser, active: false }));

    component.deleteUser(1);

    expect(notifications.error).toHaveBeenCalledWith(
      'User has activity history; deactivate the account instead',
    );
    expect(userService.setActive).toHaveBeenCalledWith(1, false);
    confirmSpy.mockRestore();
  });

  it('shows the self-delete and last-admin refusals without attempting deactivation (S12)', () => {
    for (const message of [
      'You cannot delete your own account',
      'Cannot remove the last active administrator',
    ]) {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
      userService.delete.mockReturnValue(throwError(() => ({ error: { error: message } })));

      component.deleteUser(1);

      expect(notifications.error).toHaveBeenCalledWith(message);
      expect(userService.setActive).not.toHaveBeenCalled();
      confirmSpy.mockRestore();
    }
  });
});

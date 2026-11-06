import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { ProfileComponent } from './profile.component';
import { ProfileService } from './services/profile.service';
import { ProjectService } from '../projects/services/project.service';
import { Project } from '../projects/types/project.types';
import { NotificationService } from '@app/services/notification.service';
import { ClockEntry } from '../clock/types/clock.types';
import { UserProfile } from './types/profile.types';

describe('ProfileComponent', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;
  let profileService: {
    getMe: ReturnType<typeof vi.fn>;
    updateContact: ReturnType<typeof vi.fn>;
    changePassword: ReturnType<typeof vi.fn>;
    getSessions: ReturnType<typeof vi.fn>;
    closeOtherSessions: ReturnType<typeof vi.fn>;
    setCurrentProject: ReturnType<typeof vi.fn>;
    getMyClockEntries: ReturnType<typeof vi.fn>;
    calculateHours: ReturnType<typeof vi.fn>;
    currentWeekRange: ReturnType<typeof vi.fn>;
    currentMonthRange: ReturnType<typeof vi.fn>;
  };
  let projectService: { getAll: ReturnType<typeof vi.fn> };
  let notifications: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  const profile: UserProfile = {
    id: 1,
    name: 'Ana Operaria',
    email: 'ana@example.com',
    phone: '600111222',
    nie: 'X1234567A',
    roles: [{ id: 2, name: 'OPERATOR', description: 'Field operator' }],
    availability: 'ON_SITE',
    currentProjectId: 1,
    currentProjectName: 'Obra Norte',
    active: true,
    createdAt: '2026-01-15T10:00:00',
  };

  const projectA: Project = {
    id: 1,
    clientId: 1,
    name: 'Obra Norte',
    address: 'Calle Mayor 1',
    latitude: 0,
    longitude: 0,
    status: 'IN_PROGRESS',
    active: true,
  };

  const projectB: Project = {
    id: 2,
    clientId: 1,
    name: 'Obra Sur',
    address: 'Calle Sur 2',
    latitude: 0,
    longitude: 0,
    status: 'PLANNED',
    active: true,
  };

  const session = {
    id: 'sess-1',
    createdAt: '2026-11-01T09:00:00',
    expiry: '2026-11-08T09:00:00',
  };

  const weekEntry: ClockEntry = {
    id: 1,
    userId: 1,
    projectId: 1,
    projectName: 'Obra Norte',
    clockType: 'ENTRY',
    userLatitude: 0,
    userLongitude: 0,
    timestamp: '2026-11-03T08:15:00',
  };

  const monthEntry: ClockEntry = {
    id: 2,
    userId: 1,
    projectId: 1,
    projectName: 'Obra Norte',
    clockType: 'EXIT',
    userLatitude: 0,
    userLongitude: 0,
    timestamp: '2026-11-03T17:00:00',
  };

  const native = (): HTMLElement => fixture.nativeElement as HTMLElement;

  const text = (): string => native().textContent ?? '';

  beforeEach(async () => {
    profileService = {
      getMe: vi.fn().mockReturnValue(of(profile)),
      updateContact: vi.fn(),
      changePassword: vi.fn(),
      getSessions: vi.fn().mockReturnValue(of([session])),
      closeOtherSessions: vi.fn().mockReturnValue(of(undefined)),
      setCurrentProject: vi.fn(),
      // Week range first, month range second: one week entry, two month entries.
      getMyClockEntries: vi
        .fn()
        .mockReturnValueOnce(of([weekEntry]))
        .mockReturnValueOnce(of([weekEntry, monthEntry])),
      calculateHours: vi.fn().mockImplementation((entries: ClockEntry[]) => entries.length * 4),
      currentWeekRange: vi.fn().mockReturnValue({
        start: '2026-11-02T00:00:00',
        end: '2026-11-04T15:30:00',
      }),
      currentMonthRange: vi.fn().mockReturnValue({
        start: '2026-11-01T00:00:00',
        end: '2026-11-04T15:30:00',
      }),
    };
    projectService = { getAll: vi.fn().mockReturnValue(of([projectA, projectB])) };
    notifications = { success: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        { provide: ProfileService, useValue: profileService },
        { provide: ProjectService, useValue: projectService },
        { provide: NotificationService, useValue: notifications },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the read-only identity card from GET /api/users/me (S2)', () => {
    expect(text()).toContain('Ana Operaria');
    expect(text()).toContain('ana@example.com');
    expect(text()).toContain('X1234567A');
    expect(text()).toContain('600111222');
    expect(text()).toContain('OPERATOR');
    expect(text()).toContain('En sitio');
    expect(text()).toContain('Obra Norte');
    expect(text()).toContain('15/01/2026');
    expect(profileService.getMe).toHaveBeenCalledTimes(1);
  });

  it('persists phone and nie with a payload that holds no other profile field (S3)', () => {
    const updated: UserProfile = { ...profile, phone: '600999888', nie: 'Y9999999Z' };
    profileService.updateContact.mockReturnValue(of(updated));

    component.contactPhone = '600999888';
    component.contactNie = 'Y9999999Z';
    component.saveContact();

    expect(profileService.updateContact).toHaveBeenCalledTimes(1);
    const payload = profileService.updateContact.mock.calls[0][0];
    expect(Object.keys(payload).sort()).toEqual(['nie', 'phone']);
    expect(payload).not.toHaveProperty('name');
    expect(payload).not.toHaveProperty('email');
    expect(payload).not.toHaveProperty('roles');
    expect(payload).not.toHaveProperty('active');
    expect(payload).not.toHaveProperty('availability');
    expect(component.profile()).toEqual(updated);
    expect(notifications.success).toHaveBeenCalledWith('Contacto actualizado correctamente.');
  });

  it('surfaces the backend message when the current password is wrong (S4)', () => {
    profileService.changePassword.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { error: 'Current password is incorrect' },
          }),
      ),
    );

    component.currentPassword = 'old-secret';
    component.newPassword = 'new-secret';
    component.confirmPassword = 'new-secret';
    component.savePassword();

    expect(profileService.changePassword).toHaveBeenCalledWith({
      currentPassword: 'old-secret',
      newPassword: 'new-secret',
    });
    expect(notifications.error).toHaveBeenCalledWith('Current password is incorrect');
    expect(notifications.success).not.toHaveBeenCalled();
    expect(component.currentPassword).toBe('old-secret');
  });

  it('changes the password and clears the form on 204 (S4)', () => {
    profileService.changePassword.mockReturnValue(of(null));

    component.currentPassword = 'old-secret';
    component.newPassword = 'new-secret';
    component.confirmPassword = 'new-secret';
    component.savePassword();

    expect(notifications.success).toHaveBeenCalledWith('Contraseña actualizada correctamente.');
    expect(component.currentPassword).toBe('');
    expect(component.newPassword).toBe('');
    expect(component.confirmPassword).toBe('');
  });

  it('rejects a confirmation mismatch without calling the backend', () => {
    component.currentPassword = 'old-secret';
    component.newPassword = 'new-secret';
    component.confirmPassword = 'otra';
    component.savePassword();

    expect(notifications.error).toHaveBeenCalledWith('Las contraseñas no coinciden.');
    expect(profileService.changePassword).not.toHaveBeenCalled();
  });

  it('falls back to a generic Spanish message when the error has no envelope', () => {
    profileService.changePassword.mockReturnValue(throwError(() => new Error('network down')));

    component.currentPassword = 'old-secret';
    component.newPassword = 'new-secret';
    component.confirmPassword = 'new-secret';
    component.savePassword();

    expect(notifications.error).toHaveBeenCalledWith(
      'No se pudo actualizar la contraseña. Inténtalo de nuevo.',
    );
  });

  it('offers only the assigned projects as switch options (S6)', () => {
    const options = Array.from(native().querySelectorAll('app-select option')).map((option) =>
      option.textContent?.trim(),
    );

    expect(projectService.getAll).toHaveBeenCalledTimes(1);
    expect(options).toContain('Obra Norte');
    expect(options).toContain('Obra Sur');
    expect(options).toHaveLength(3); // placeholder + the two assigned projects
  });

  it('switches the current project through PUT /api/users/me/current-project (S6)', () => {
    profileService.setCurrentProject.mockReturnValue(
      of({ ...profile, currentProjectId: 2, currentProjectName: 'Obra Sur' }),
    );

    component.selectedProjectId = '2';
    component.saveProject();

    expect(profileService.setCurrentProject).toHaveBeenCalledWith(2);
    expect(component.profile()?.currentProjectName).toBe('Obra Sur');
    expect(notifications.success).toHaveBeenCalledWith('Proyecto actual actualizado.');
  });

  it('surfaces the 403 envelope and keeps the profile on an unassigned project (S6)', () => {
    profileService.setCurrentProject.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            error: { error: 'You are not assigned to this project' },
          }),
      ),
    );

    component.selectedProjectId = '2';
    component.saveProject();

    expect(notifications.error).toHaveBeenCalledWith('You are not assigned to this project');
    expect(component.profile()?.currentProjectName).toBe('Obra Norte');
    expect(notifications.success).not.toHaveBeenCalled();
  });

  it('fetches the caller entries for the week and month ranges (S7)', () => {
    expect(profileService.getMyClockEntries).toHaveBeenCalledWith(
      '2026-11-02T00:00:00',
      '2026-11-04T15:30:00',
    );
    expect(profileService.getMyClockEntries).toHaveBeenCalledWith(
      '2026-11-01T00:00:00',
      '2026-11-04T15:30:00',
    );
  });

  it('shows the week and month hours for the caller entries (S7)', () => {
    expect(text()).toContain('4,0 h');
    expect(text()).toContain('8,0 h');
  });

  it('renders the recent clock entries with Spanish labels (S7)', () => {
    expect(text()).toContain('Obra Norte');
    expect(text()).toContain('Entrada');
    expect(text()).toContain('Salida');
    expect(text()).toContain('03/11/2026 17:00');
    expect(text()).toContain('03/11/2026 08:15');
  });

  it('lists the active sessions with id, createdAt and expiry (S8)', () => {
    expect(text()).toContain('sess-1');
    expect(text()).toContain('01/11/2026 09:00');
    expect(text()).toContain('08/11/2026 09:00');
  });

  it('closes the other sessions and reloads the list (S8)', () => {
    component.closeOtherSessions();

    expect(profileService.closeOtherSessions).toHaveBeenCalledTimes(1);
    expect(notifications.success).toHaveBeenCalledWith('Se cerraron las demás sesiones.');
    expect(profileService.getSessions).toHaveBeenCalledTimes(2);
  });

  it('keeps the page as separated card sections with a reserved slot (S13)', () => {
    expect(native().querySelectorAll('app-card').length).toBeGreaterThanOrEqual(7);
    for (const title of [
      'Identidad',
      'Contacto',
      'Cambiar contraseña',
      'Proyecto actual',
      'Mis fichajes',
      'Mis sesiones',
    ]) {
      expect(text()).toContain(title);
    }
    expect(text()).toContain('Reservado para futuros módulos');
  });
});

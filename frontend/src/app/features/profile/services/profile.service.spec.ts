import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProfileService } from './profile.service';
import { AuthService } from '@app/auth/services/auth.service';
import { ClockEntry } from '../../clock/types/clock.types';
import { UserProfile } from '../types/profile.types';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  const profile: UserProfile = {
    id: 1,
    name: 'Ana Operaria',
    email: 'ana@example.com',
    phone: '600111222',
    nie: 'X1234567A',
    roles: [{ id: 2, name: 'OPERATOR', description: 'Field operator' }],
    availability: 'AVAILABLE',
    currentProjectId: 1,
    currentProjectName: 'Obra Norte',
    active: true,
    createdAt: '2026-01-15T10:00:00',
  };

  const entry = (timestamp: string): ClockEntry => ({
    id: 1,
    userId: 1,
    projectId: 1,
    clockType: 'ENTRY',
    userLatitude: 0,
    userLongitude: 0,
    timestamp,
  });

  const exit = (timestamp: string): ClockEntry => ({
    ...entry(timestamp),
    clockType: 'EXIT',
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: { getRefreshToken: vi.fn().mockReturnValue('refresh-token-abc') },
        },
      ],
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('HTTP contract', () => {
    it('getMe reads the self profile from GET /api/users/me (S2)', () => {
      service.getMe().subscribe((result) => expect(result).toEqual(profile));

      const req = httpMock.expectOne('/api/users/me');
      expect(req.request.method).toBe('GET');
      req.flush(profile);
    });

    it('updateContact PUTs only phone and nie to PUT /api/users/me (S3)', () => {
      const updated: UserProfile = { ...profile, phone: '600999888', nie: 'Y9999999Z' };
      service.updateContact({ phone: '600999888', nie: 'Y9999999Z' }).subscribe((result) => {
        expect(result).toEqual(updated);
      });

      const req = httpMock.expectOne('/api/users/me');
      expect(req.request.method).toBe('PUT');
      expect(Object.keys(req.request.body).sort()).toEqual(['nie', 'phone']);
      expect(req.request.body).toEqual({ phone: '600999888', nie: 'Y9999999Z' });
      req.flush(updated);
    });

    it('changePassword POSTs current and new password to /api/users/me/password (S4)', () => {
      service.changePassword({ currentPassword: 'old-secret', newPassword: 'new-secret' }).subscribe();

      const req = httpMock.expectOne('/api/users/me/password');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        currentPassword: 'old-secret',
        newPassword: 'new-secret',
      });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('getSessions reads the active sessions from GET /api/users/me/sessions (S8)', () => {
      const sessions = [{ id: 'sess-1', createdAt: '2026-11-01T09:00:00', expiry: '2026-11-08T09:00:00' }];
      service.getSessions().subscribe((result) => expect(result).toEqual(sessions));

      const req = httpMock.expectOne('/api/users/me/sessions');
      expect(req.request.method).toBe('GET');
      req.flush(sessions);
    });

    it('closeOtherSessions DELETEs the current refresh token to /api/users/me/sessions (S8)', () => {
      service.closeOtherSessions().subscribe();

      const req = httpMock.expectOne('/api/users/me/sessions');
      expect(req.request.method).toBe('DELETE');
      // The current session's refresh token comes from AuthService.getRefreshToken().
      expect(req.request.body).toEqual({ refreshToken: 'refresh-token-abc' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('setCurrentProject PUTs { projectId } to /api/users/me/current-project (S6)', () => {
      const updated: UserProfile = { ...profile, currentProjectId: 2, currentProjectName: 'Obra Sur' };
      service.setCurrentProject(2).subscribe((result) => expect(result).toEqual(updated));

      const req = httpMock.expectOne('/api/users/me/current-project');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ projectId: 2 });
      req.flush(updated);
    });

    it('getMyClockEntries reads /api/clock-entries/me with start and end params (S7)', () => {
      const entries = [entry('2026-11-03T08:15:00')];
      service
        .getMyClockEntries('2026-11-02T00:00:00', '2026-11-04T15:30:00')
        .subscribe((result) => expect(result).toEqual(entries));

      const req = httpMock.expectOne(
        (r) =>
          r.url === '/api/clock-entries/me' &&
          r.params.get('start') === '2026-11-02T00:00:00' &&
          r.params.get('end') === '2026-11-04T15:30:00',
      );
      expect(req.request.method).toBe('GET');
      req.flush(entries);
    });
  });

  describe('hours calculation (S7)', () => {
    it('pairs each ENTRY with the following EXIT and sums hours', () => {
      expect(
        service.calculateHours([entry('2026-11-03T09:00:00'), exit('2026-11-03T17:00:00')]),
      ).toBe(8);
    });

    it('supports half-hour precision', () => {
      expect(
        service.calculateHours([entry('2026-11-03T09:00:00'), exit('2026-11-03T12:30:00')]),
      ).toBe(3.5);
    });

    it('sorts entries ascending before pairing, whatever the input order', () => {
      expect(
        service.calculateHours([exit('2026-11-03T17:00:00'), entry('2026-11-03T09:00:00')]),
      ).toBe(8);
    });

    it('ignores an EXIT that has no preceding ENTRY', () => {
      expect(
        service.calculateHours([
          exit('2026-11-03T08:00:00'),
          entry('2026-11-03T09:00:00'),
          exit('2026-11-03T17:00:00'),
        ]),
      ).toBe(8);
    });

    it('gives a trailing ENTRY with no EXIT zero hours (odd count)', () => {
      expect(
        service.calculateHours([
          entry('2026-11-03T09:00:00'),
          exit('2026-11-03T12:00:00'),
          entry('2026-11-03T13:00:00'),
        ]),
      ).toBe(3);
    });

    it('returns zero for a single unpaired ENTRY', () => {
      expect(service.calculateHours([entry('2026-11-03T09:00:00')])).toBe(0);
    });

    it('keeps the first pending ENTRY when a duplicate ENTRY arrives', () => {
      expect(
        service.calculateHours([
          entry('2026-11-03T09:00:00'),
          entry('2026-11-03T09:30:00'),
          exit('2026-11-03T17:00:00'),
        ]),
      ).toBe(8);
    });
  });

  describe('date ranges (S7)', () => {
    const now = new Date(2026, 10, 4, 15, 30, 15, 250); // Wednesday 2026-11-04, local time

    it('builds the current week range from Monday 00:00:00 to now', () => {
      expect(service.currentWeekRange(now)).toEqual({
        start: '2026-11-02T00:00:00',
        end: '2026-11-04T15:30:15',
      });
    });

    it('starts the week on Monday even when now is a Sunday', () => {
      const sunday = new Date(2026, 10, 8, 10, 0, 0);
      expect(service.currentWeekRange(sunday).start).toBe('2026-11-02T00:00:00');
    });

    it('builds the current month range from the 1st 00:00:00 to now', () => {
      expect(service.currentMonthRange(now)).toEqual({
        start: '2026-11-01T00:00:00',
        end: '2026-11-04T15:30:15',
      });
    });

    it('formats ranges as ISO DATE_TIME query params without a timezone suffix', () => {
      const week = service.currentWeekRange(now);
      expect(week.start).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/);
      expect(week.end).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/);
    });
  });
});

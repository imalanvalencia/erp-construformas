import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '@app/auth/services/auth.service';
import { ClockEntry } from '../../clock/types/clock.types';
import {
  ChangePasswordRequest,
  ClockRange,
  ProfileContactUpdate,
  ProfileSession,
  UserProfile,
} from '../types/profile.types';

/**
 * Self-scoped profile API (S2/S3/S4/S6/S7/S8) plus the S7 hours calculation,
 * defined once here as a pure function so every profile surface shares it.
 */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly API = '/api/users/me';

  private http = inject(HttpClient);
  private auth = inject(AuthService);

  getMe(): Observable<UserProfile> {
    return this.http.get<UserProfile>(this.API);
  }

  /** Only `phone` and `nie` travel in the body; everything else is admin-only (S3). */
  updateContact(update: ProfileContactUpdate): Observable<UserProfile> {
    return this.http.put<UserProfile>(this.API, update);
  }

  /** 204 on success; 400 `Current password is incorrect` on a mismatch (S4). */
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.API}/password`, request);
  }

  getSessions(): Observable<ProfileSession[]> {
    return this.http.get<ProfileSession[]>(`${this.API}/sessions`);
  }

  /**
   * S8: revokes every refresh token except the caller's current one, which comes
   * from `AuthService.getRefreshToken()` so the active session stays usable.
   */
  closeOtherSessions(): Observable<void> {
    const refreshToken = this.auth.getRefreshToken();
    return this.http.delete<void>(`${this.API}/sessions`, { body: { refreshToken } });
  }

  /**
   * S6: the caller moves itself among its assigned projects; the 403 for an
   * unassigned project and the 400s arrive in the `{ "error": ... }` envelope.
   */
  setCurrentProject(projectId: number): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.API}/current-project`, { projectId });
  }

  /** S7: self-scoped; the backend only ever returns the caller's own entries. */
  getMyClockEntries(start: string, end: string): Observable<ClockEntry[]> {
    return this.http.get<ClockEntry[]>('/api/clock-entries/me', { params: { start, end } });
  }

  /**
   * S7 hours: entries sorted ascending, each EXIT paired with the pending ENTRY.
   * An EXIT with no pending ENTRY is ignored, a duplicate ENTRY while one is
   * pending keeps the first one, and a trailing ENTRY with no EXIT adds nothing.
   * Returns hours (0.5 = 30 minutes).
   */
  calculateHours(entries: ClockEntry[]): number {
    const sorted = [...entries].sort(
      (a, b) => Date.parse(a.timestamp ?? '') - Date.parse(b.timestamp ?? ''),
    );

    let pendingEntry: number | null = null;
    let totalMs = 0;

    for (const item of sorted) {
      const timestamp = Date.parse(item.timestamp ?? '');
      if (Number.isNaN(timestamp)) continue;

      if (item.clockType === 'ENTRY') {
        if (pendingEntry === null) pendingEntry = timestamp;
      } else if (pendingEntry !== null) {
        totalMs += timestamp - pendingEntry;
        pendingEntry = null;
      }
    }

    return totalMs / 3_600_000;
  }

  /** Current week: Monday 00:00:00 (local) → now, as ISO DATE_TIME params. */
  currentWeekRange(now: Date = new Date()): ClockRange {
    const daysSinceMonday = (now.getDay() + 6) % 7;
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - daysSinceMonday);
    return { start: toIsoLocalDateTime(start), end: toIsoLocalDateTime(now) };
  }

  /** Current month: the 1st 00:00:00 (local) → now, as ISO DATE_TIME params. */
  currentMonthRange(now: Date = new Date()): ClockRange {
    const start = new Date(now.getFullYear(), now.getMonth(), 1);
    return { start: toIsoLocalDateTime(start), end: toIsoLocalDateTime(now) };
  }
}

/** Local `YYYY-MM-DDTHH:mm:ss`, the format Spring binds to `LocalDateTime`. */
function toIsoLocalDateTime(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

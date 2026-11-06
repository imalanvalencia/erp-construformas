import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, PasswordResetResponse, Role } from '../types/user.types';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly API = '/api/users';

  private http = inject(HttpClient);

  /** ADMIN only. The payload never carries `password` — it is write-only server-side. */
  getAll(): Observable<User[]> {
    return this.http.get<User[]>(this.API);
  }

  getById(id: number): Observable<User> {
    return this.http.get<User>(`${this.API}/${id}`);
  }

  create(user: User): Observable<User> {
    return this.http.post<User>(this.API, user);
  }

  update(id: number, user: User): Observable<User> {
    return this.http.put<User>(`${this.API}/${id}`, user);
  }

  /**
   * S9: `PATCH /api/users/{id}/active`. The backend answers 409 when you deactivate
   * your own account or when the last active ADMIN would lose its access, and both
   * refusals arrive in the `{ "error": "..." }` envelope.
   */
  setActive(id: number, active: boolean): Observable<User> {
    return this.http.patch<User>(`${this.API}/${id}/active`, { active });
  }

  /**
   * S10: `POST /api/users/{id}/password-reset`. The plain-text password is returned
   * exactly once — the backend only ever stores its hash.
   */
  resetPassword(id: number): Observable<PasswordResetResponse> {
    return this.http.post<PasswordResetResponse>(`${this.API}/${id}/password-reset`, null);
  }

  /**
   * S11: `PUT /api/users/{id}/current-project` — ADMIN only. Refusals arrive in the
   * `{ "error": "..." }` envelope (400 unknown/missing project, 403 not assigned).
   */
  setCurrentProject(id: number, projectId: number): Observable<User> {
    return this.http.put<User>(`${this.API}/${id}/current-project`, { projectId });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/${id}`);
  }

  /**
   * Role catalog from GET /api/roles (ADMIN only). The many-to-many `roles` payload
   * needs real ids, and this is the only way to obtain them.
   */
  getRoles(): Observable<Role[]> {
    return this.http.get<Role[]>('/api/roles');
  }
}

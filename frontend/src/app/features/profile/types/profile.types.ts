import { Role, UserAvailability } from '../../users/types/user.types';

/**
 * Mirrors the backend `UserProfileDTO` returned by `GET /api/users/me`.
 * Deliberately password-free: the backend never serialises the hash into it.
 */
export interface UserProfile {
  id: number;
  name: string;
  email: string;
  phone?: string | null;
  nie?: string | null;
  roles: Role[];
  availability: UserAvailability;
  currentProjectId?: number | null;
  currentProjectName?: string | null;
  active: boolean;
  createdAt?: string | null;
}

/**
 * The only fields their owner may edit through `PUT /api/users/me` (S3):
 * `name`, `email`, `roles`, `active` and `availability` stay admin-only.
 * `null`/omitted keeps the stored value; an empty string clears it.
 */
export interface ProfileContactUpdate {
  phone?: string | null;
  nie?: string | null;
}

/** Body of `POST /api/users/me/password` (S4). Both values are required. */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** Mirrors the backend `SessionDTO` of `GET /api/users/me/sessions` (S8). */
export interface ProfileSession {
  id: string;
  createdAt: string;
  expiry: string;
}

/** ISO DATE_TIME query params (local, no timezone suffix) for `/api/clock-entries/me` (S7). */
export interface ClockRange {
  start: string;
  end: string;
}

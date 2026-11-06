import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let http: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    put: ReturnType<typeof vi.fn>;
    patch: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    http = {
      get: vi.fn().mockReturnValue(of([])),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      patch: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [UserService, { provide: HttpClient, useValue: http }],
    });
    service = TestBed.inject(UserService);
  });

  it('setActive PATCHes the boolean flag to /{id}/active (S9)', () => {
    service.setActive(7, false).subscribe();

    expect(http.patch).toHaveBeenCalledWith('/api/users/7/active', { active: false });
  });

  it('resetPassword POSTs to /{id}/password-reset and returns the one-shot password (S10)', () => {
    http.post.mockReturnValue(of({ temporaryPassword: 'Tmp-1a2B!' }));

    service.resetPassword(7).subscribe((response) => {
      expect(response.temporaryPassword).toBe('Tmp-1a2B!');
    });

    expect(http.post).toHaveBeenCalledWith('/api/users/7/password-reset', null);
  });

  it('setCurrentProject PUTs the projectId to /{id}/current-project (S11)', () => {
    service.setCurrentProject(3, 9).subscribe();

    expect(http.put).toHaveBeenCalledWith('/api/users/3/current-project', { projectId: 9 });
  });
});

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../services/auth.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  const setup = (isAdmin: boolean) => {
    const createUrlTree = vi.fn().mockReturnValue('redirect-tree');
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { hasRole: vi.fn().mockReturnValue(isAdmin) } },
        { provide: Router, useValue: { createUrlTree } },
      ],
    });
    return { createUrlTree };
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('allows an ADMIN', () => {
    setup(true);

    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    expect(result).toBe(true);
  });

  it('redirects a non-admin to the dashboard', () => {
    const { createUrlTree } = setup(false);

    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    expect(result).toBe('redirect-tree');
    expect(createUrlTree).toHaveBeenCalledWith(['/']);
  });
});

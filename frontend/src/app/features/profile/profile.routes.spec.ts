import { describe, expect, it } from 'vitest';
import { routes } from '@app/app.routes';
import { authGuard } from '@app/auth/guards/auth.guard';
import { ProfileComponent } from './profile.component';

describe('profile routes (S1)', () => {
  const shell = routes.find((route) => route.path === '' && route.children);

  it('exposes the profile route inside the authenticated shell', () => {
    const profileRoute = (shell?.children ?? []).find((route) => route.path === 'profile');
    expect(profileRoute).toBeTruthy();
    expect(profileRoute?.component).toBe(ProfileComponent);
  });

  it('keeps the profile reachable only behind the authGuard', () => {
    expect(shell?.canActivate).toEqual(expect.arrayContaining([authGuard]));
  });
});

import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';
import { ClockComponent } from './features/clock/clock.component';
import { adminGuard } from './auth/guards/admin.guard';
import { UsersComponent } from './features/users/users.component';

describe('app routes', () => {
  const shellChildren = routes.find((route) => route.path === '' && route.children)?.children ?? [];

  it('exposes the clock route inside the authenticated shell (S6)', () => {
    const clockRoute = shellChildren.find((route) => route.path === 'clock');
    expect(clockRoute).toBeTruthy();
    expect(clockRoute?.component).toBe(ClockComponent);
  });

  it('protects the users route with the admin guard', () => {
    const usersRoute = shellChildren.find((route) => route.path === 'users');
    expect(usersRoute).toBeTruthy();
    expect(usersRoute?.component).toBe(UsersComponent);
    expect(usersRoute?.canActivate).toEqual(expect.arrayContaining([adminGuard]));
  });
});

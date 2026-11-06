import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { AuthService } from '@auth/services/auth.service';

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;

  const compile = async (roles: string[]) => {
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            getUser: vi.fn().mockReturnValue({ id: 1, name: 'Test', email: 't@test.com', roles }),
            hasRole: vi.fn().mockImplementation((role: string) => roles.includes(role)),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
  };

  const native = (): HTMLElement => fixture.nativeElement as HTMLElement;

  const labels = (): string[] =>
    Array.from((native() as HTMLElement).querySelectorAll('app-nav-link')).map((el) =>
      (el as HTMLElement).textContent?.trim() ?? '',
    );

  const clockIcon = (): string | null => {
    const item = Array.from((native() as HTMLElement).querySelectorAll('app-nav-link')).find((el) =>
      (el as HTMLElement).textContent?.includes('Fichajes'),
    );
    return item?.querySelector('mat-icon')?.textContent?.trim() ?? null;
  };

  describe('Usuarios entry', () => {
    it('shows the entry to an ADMIN', async () => {
      await compile(['ADMIN']);

      expect(labels().some((l) => l.includes('Usuarios'))).toBe(true);
    });

    it('hides the entry from an OPERATOR', async () => {
      await compile(['OPERATOR']);

      expect(labels().some((l) => l.includes('Usuarios'))).toBe(false);
    });
  });

  describe('Fichajes entry (S6)', () => {
    it('shows the entry with a clock icon to an OPERATOR', async () => {
      await compile(['OPERATOR']);

      expect(labels().some((l) => l.includes('Fichajes'))).toBe(true);
      expect(clockIcon()).toBe('schedule');
      expect(clockIcon()).not.toBe('home');
    });

    it('shows the entry to an ADMIN', async () => {
      await compile(['ADMIN']);

      expect(labels().some((l) => l.includes('Fichajes'))).toBe(true);
    });

    it('hides the entry from roles outside the ADMIN/OPERATOR scope', async () => {
      await compile(['MANAGER']);

      expect(labels().some((l) => l.includes('Fichajes'))).toBe(false);
    });
  });
});

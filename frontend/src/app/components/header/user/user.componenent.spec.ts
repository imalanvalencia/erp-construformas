import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UserComponenent } from './user.componenent';
import { AuthService } from '@app/auth/services/auth.service';

describe('UserComponenent', () => {
  let fixture: ComponentFixture<UserComponenent>;

  /** `mat-menu` renders its content lazily into the overlay, so open it first. */
  const openMenuLink = async (): Promise<HTMLAnchorElement | null> => {
    const trigger = (fixture.nativeElement as HTMLElement).querySelector('button');
    trigger?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return document.querySelector('.cdk-overlay-container a');
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserComponenent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { logout: vi.fn(), getRefreshToken: vi.fn().mockReturnValue('rt-1') },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UserComponenent);
    fixture.detectChanges();
  });

  it('navigates the menu entry to /profile instead of the dead /settings route (S1)', async () => {
    expect((await openMenuLink())?.getAttribute('href')).toBe('/profile');
  });

  it('keeps the Spanish label of the profile entry (S1)', async () => {
    expect((await openMenuLink())?.textContent).toContain('Configuracion');
  });
});

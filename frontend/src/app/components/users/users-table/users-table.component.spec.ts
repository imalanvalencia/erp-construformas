import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UsersTableComponent } from './users-table.component';
import { User } from '../../../features/users/types/user.types';

describe('UsersTableComponent', () => {
  let fixture: ComponentFixture<UsersTableComponent>;
  let component: UsersTableComponent;

  const activeUser: User = {
    id: 1,
    name: 'Ana Uno',
    email: 'ana@example.com',
    roles: [],
    active: true,
  };
  const inactiveUser: User = {
    id: 2,
    name: 'Beto Dos',
    email: 'beto@example.com',
    roles: [],
    active: false,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [UsersTableComponent] }).compileComponents();
    fixture = TestBed.createComponent(UsersTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const rows = (): HTMLElement[] =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr'));

  const buttonIn = (row: HTMLElement, label: string): HTMLButtonElement | undefined =>
    Array.from(row.querySelectorAll('button')).find((button) =>
      (button.textContent ?? '').includes(label),
    );

  it('has no availability column anymore (S5)', () => {
    fixture.componentRef.setInput('users', [activeUser]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Disponibilidad');
    expect(text).not.toContain('Disponible');
  });

  it('offers activate/deactivate, password reset and project assignment per row (S9-S11)', () => {
    fixture.componentRef.setInput('users', [activeUser, inactiveUser]);
    fixture.detectChanges();

    const [activeRow, inactiveRow] = rows();
    expect(buttonIn(activeRow, 'Deactivate')).toBeTruthy();
    expect(buttonIn(activeRow, 'Reset password')).toBeTruthy();
    expect(buttonIn(activeRow, 'Project')).toBeTruthy();
    expect(buttonIn(inactiveRow, 'Activate')).toBeTruthy();
  });

  it('emits onToggleActive with the row user (S9)', () => {
    fixture.componentRef.setInput('users', [activeUser]);
    fixture.detectChanges();
    const spy = vi.fn();
    const subscription = component.onToggleActive.subscribe(spy);

    buttonIn(rows()[0], 'Deactivate')?.click();

    expect(spy).toHaveBeenCalledWith(activeUser);
    subscription.unsubscribe();
  });

  it('emits onResetPassword with the row user (S10)', () => {
    fixture.componentRef.setInput('users', [activeUser]);
    fixture.detectChanges();
    const spy = vi.fn();
    const subscription = component.onResetPassword.subscribe(spy);

    buttonIn(rows()[0], 'Reset password')?.click();

    expect(spy).toHaveBeenCalledWith(activeUser);
    subscription.unsubscribe();
  });

  it('emits onAssignProject with the row user (S11)', () => {
    fixture.componentRef.setInput('users', [activeUser]);
    fixture.detectChanges();
    const spy = vi.fn();
    const subscription = component.onAssignProject.subscribe(spy);

    buttonIn(rows()[0], 'Project')?.click();

    expect(spy).toHaveBeenCalledWith(activeUser);
    subscription.unsubscribe();
  });

  it('keeps the existing edit and delete actions (S12)', () => {
    fixture.componentRef.setInput('users', [activeUser]);
    fixture.detectChanges();

    const editSpy = vi.fn();
    const deleteSpy = vi.fn();
    const editSubscription = component.onEdit.subscribe(editSpy);
    const deleteSubscription = component.onDelete.subscribe(deleteSpy);

    buttonIn(rows()[0], 'Editar')?.click();
    buttonIn(rows()[0], 'Eliminar')?.click();

    expect(editSpy).toHaveBeenCalledWith(activeUser);
    expect(deleteSpy).toHaveBeenCalledWith(1);
    editSubscription.unsubscribe();
    deleteSubscription.unsubscribe();
  });
});
